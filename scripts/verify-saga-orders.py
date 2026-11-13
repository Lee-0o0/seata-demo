#!/usr/bin/env python3
"""End-to-end Saga checks using isolated resources and local fault proxies."""
import hashlib
import concurrent.futures
import http.server
import json
import os
import pathlib
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.request
import uuid
import runpy
helpers = runpy.run_path(str(pathlib.Path(__file__).with_name('verify-saga-participants.py')))
sql, ROOT = helpers['sql'], helpers['ROOT']

def request(port, path, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(f'http://127.0.0.1:{port}{path}', data=data,
                                 headers={'Content-Type': 'application/json'})
    try:
        response = urllib.request.urlopen(req, timeout=30)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        content = response.read()
        return response.code, json.loads(content) if content else None

processes = {}
logs = {}
fault = {'stock_response': False, 'account_response': False, 'compensation': False, 'hold': False}
held = threading.Event()
release = threading.Event()

def proxy(target, name):
    class Handler(http.server.BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass
        def do_POST(self):
            data = self.rfile.read(int(self.headers.get('Content-Length', 0)))
            compensation = self.path.endswith('/compensate')
            if compensation and fault['compensation']:
                self.send_response(503); self.end_headers(); return
            req = urllib.request.Request(f'http://127.0.0.1:{target}{self.path}', data=data,
                                         headers={'Content-Type': 'application/json'})
            try:
                with urllib.request.urlopen(req, timeout=30) as response:
                    status, body = response.status, response.read()
            except urllib.error.HTTPError as error:
                status, body = error.code, error.read()
            if not compensation and fault[name + '_response'] and status == 204:
                status = 502
            if name == 'account' and not compensation and fault['hold'] and status == 204:
                held.set(); release.wait(45)
            self.send_response(status); self.end_headers()
            try:
                self.wfile.write(body)
            except (BrokenPipeError, ConnectionResetError):
                pass
    server = http.server.ThreadingHTTPServer(('127.0.0.1', target + 1000), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server

def start(module, port):
    log = tempfile.TemporaryFile(mode='w+')
    logs.setdefault(module, []).append(log)
    env = os.environ.copy()
    env.update(STOCK_SERVICE_URL='http://127.0.0.1:19081', ACCOUNT_SERVICE_URL='http://127.0.0.1:19083')
    process = subprocess.Popen(['java', '-jar', str(ROOT / module / 'target' / f'{module}-0.0.1-SNAPSHOT.jar'),
                                f'--server.port={port}'], stdout=log, stderr=log, env=env, cwd=ROOT)
    processes[module] = process
    for _ in range(120):
        try:
            request(port, '/not-found')
            return
        except (OSError, urllib.error.URLError):
            pass
        if process.poll() is not None:
            raise AssertionError(module + ' exited during startup')
        time.sleep(.5)
    raise AssertionError(module + ' startup timeout')

def query(service, database, statement):
    return sql(service, database, 'MYSQL_USER', 'MYSQL_PASSWORD', statement)

def wait_closed(order_id, timeout=90):
    for _ in range(timeout * 2):
        status, order = request(18082, f'/orders/{order_id}')
        if status == 200 and order['status'] == 'CLOSED':
            return order
        time.sleep(.5)
    raise AssertionError(f'Order {order_id} not compensated: {status}, {order}')

def main():
    proxies = [proxy(18081, 'stock'), proxy(18083, 'account')]
    try:
        for module, port in [('stock-service', 18081), ('account-service', 18083), ('order-service', 18082)]:
            start(module, port)
        resource = time.time_ns() // 1000
        query('stock-mysql', 'stock_db', f'INSERT INTO stock(product_id,quantity) VALUES({resource},100);')
        query('account-mysql', 'account_db', f'INSERT INTO account(user_id,available_balance) VALUES({resource},1000);')
        query('account-mysql', 'account_db', f'INSERT INTO account(user_id,available_balance) VALUES({resource+1},1);')
        query('order-mysql', 'order_db', f"INSERT INTO product(id,name,price) VALUES({resource},'Saga verification',10);")
        def create(**extra):
            data = dict(userId=resource, productId=resource, quantity=2, requestId='verify-' + uuid.uuid4().hex)
            data.update(extra)
            return request(18082, '/orders', data)
        def balances():
            return (request(18081, f'/stocks/{resource}')[1]['quantity'],
                    request(18083, f'/accounts/{resource}')[1]['availableBalance'])
        request_id = 'verify-' + uuid.uuid4().hex
        status, order = create(requestId=request_id)
        assert status == 201 and order['status'] == 'SUCCESS', (status, order)
        assert balances() == (98, 980)
        nodes = query('order-mysql', 'order_db', f"SELECT s.name FROM seata_state_inst s JOIN seata_state_machine_inst m ON m.id=s.machine_inst_id WHERE m.business_key='order:{hashlib.sha256(request_id.encode()).hexdigest()}' AND s.name IN ('CreateOrder','DeductStock','DebitAccount','ConfirmOrder') AND s.status='SU';")
        assert set(nodes.splitlines()) == {'CreateOrder', 'DeductStock', 'DebitAccount', 'ConfirmOrder'}, nodes
        assert query('order-mysql', 'order_db', f"SELECT status FROM purchase_order WHERE id={order['id']};") == 'SUCCESS'
        with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
            duplicates = list(pool.map(lambda _: create(requestId=request_id), range(6)))
        assert all(s == 201 and o['id'] == order['id'] for s, o in duplicates), duplicates
        assert balances() == (98, 980)
        assert create(requestId=request_id, quantity=3)[0] == 409
        print('PASS success and concurrent idempotent order submission', flush=True)
        # Persist an instance with startup parameters but no order, as at an interrupted CreateOrder.
        pending_request = 'verify-before-create-' + uuid.uuid4().hex
        pending_key = 'order:' + hashlib.sha256(pending_request.encode()).hexdigest()
        original_key = 'order:' + hashlib.sha256(request_id.encode()).hexdigest()
        pending_instance = 'verify-instance-' + uuid.uuid4().hex
        query('order-mysql', 'order_db', f"INSERT INTO seata_state_machine_inst(id,machine_id,tenant_id,business_key,gmt_started,gmt_updated,status,is_running,start_params) SELECT '{pending_instance}',machine_id,tenant_id,'{pending_key}',NOW(3),NOW(3),'RU',1,JSON_SET(start_params,'$.requestId','{pending_request}') FROM seata_state_machine_inst WHERE business_key='{original_key}' AND tenant_id='order';")
        before = balances()
        for changed in [dict(userId=resource+1), dict(productId=resource+1), dict(quantity=3), dict(exception=True)]:
            assert create(requestId=pending_request, **changed)[0] == 409, changed
        # Same parameters reuse the instance and report that order creation is still pending.
        assert create(requestId=pending_request)[0] == 503
        assert query('order-mysql', 'order_db', f"SELECT COUNT(*) FROM purchase_order WHERE request_id='{pending_request}';") == '0'
        assert query('order-mysql', 'order_db', f"SELECT COUNT(*) FROM seata_state_inst WHERE machine_inst_id='{pending_instance}';") == '0'
        assert balances() == before
        print('PASS startup parameter validation before order creation, without executing nodes', flush=True)
        for options in [dict(quantity=200), dict(userId=resource+1), dict(exception=True)]:
            before = balances()
            status, failed = create(**options)
            assert status == 409 and failed['status'] == 'CLOSED', (options, status, failed)
            assert balances() == before, (options, balances(), before)
            assert query('order-mysql', 'order_db', f"SELECT status FROM purchase_order WHERE id={failed['id']};") == 'CLOSED'
        print('PASS stock rejection, insufficient balance and failure after both deductions', flush=True)
        for name in ['stock_response', 'account_response']:
            before = balances(); fault[name] = True
            status, failed = create()
            fault[name] = False
            assert status == 409 and failed['status'] == 'CLOSED', (name, status, failed)
            assert balances() == before
        print('PASS committed remote deductions with lost/error responses are compensated', flush=True)
        before = balances(); fault['compensation'] = True
        status, pending = create(exception=True)
        assert status == 202 and pending['status'] == 'COMPENSATING', (status, pending)
        assert balances() == (before[0]-2, before[1]-20)
        fault['compensation'] = False
        wait_closed(pending['id'])
        assert balances() == before
        print('PASS compensation failure recovered by Seata Server retry', flush=True)
        # Kill the orchestrator after account commits but before the node receives its response.
        before = balances(); fault['hold'] = True
        crash_request = 'verify-crash-' + uuid.uuid4().hex
        with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
            future = pool.submit(create, requestId=crash_request)
            assert held.wait(30), 'account call did not reach crash point'
            processes['order-service'].kill(); processes['order-service'].wait(20)
            fault['hold'] = False; release.set()
            try:
                future.result(timeout=10)
            except Exception:
                pass
        start('order-service', 18082)
        order_id = int(query('order-mysql', 'order_db', f"SELECT id FROM purchase_order WHERE request_id='{crash_request}';"))
        wait_closed(order_id, 120)
        assert balances() == before
        print('PASS orchestrator crash after remote commit, restart and compensation recovery', flush=True)
        # Invalid requests must not create orders or start a state machine.
        invalid_request = 'verify-invalid-' + uuid.uuid4().hex
        assert create(requestId=invalid_request, quantity=0)[0] == 400
        assert query('order-mysql', 'order_db', f"SELECT COUNT(*) FROM purchase_order WHERE request_id='{invalid_request}';") == '0'
        print('PASS invalid request leaves no order', flush=True)
    except BaseException:
        for name, files in logs.items():
            for log in files:
                log.seek(0); print(name + '\n' + log.read()[-18000:])
        raise
    finally:
        release.set()
        for process in processes.values():
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=20)
                except subprocess.TimeoutExpired:
                    process.kill(); process.wait()
        for server in proxies:
            server.shutdown(); server.server_close()
        for files in logs.values():
            for log in files:
                log.close()

if __name__ == '__main__':
    main()
