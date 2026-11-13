#!/usr/bin/env python3
"""Verify the two Saga participants against the Compose MySQL databases."""
import concurrent.futures
import json
import pathlib
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[1]

def sql(service, database, user_env, password_env, statement):
    return subprocess.check_output([
        'docker', 'compose', 'exec', '-T', service, 'sh', '-c',
        f'MYSQL_PWD=${password_env} mysql -u${user_env} {database} -N -B',
    ], input=statement, text=True, cwd=ROOT).strip()

def request(port, path, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(f'http://127.0.0.1:{port}{path}', data=data,
                                 headers={'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            content = response.read()
            return response.status, json.loads(content) if content else None
    except urllib.error.HTTPError as error:
        return error.code, None

def verify(module, port, prefix, key, value_key, initial, amount, db_service, database, table, column):
    resource = time.time_ns() // 1000
    operation = 'verify-' + uuid.uuid4().hex
    query = lambda statement: sql(db_service, database, 'MYSQL_USER', 'MYSQL_PASSWORD', statement)
    fields = f'{key},{column}'
    values = f'{resource},{initial}'
    log = tempfile.TemporaryFile(mode='w+')
    process = subprocess.Popen(['java', '-jar', str(ROOT / module / 'target' / f'{module}-0.0.1-SNAPSHOT.jar'),
                                f'--server.port={port}'], stdout=log, stderr=log, cwd=ROOT)
    deduct_path = prefix + ('/deduct' if table == 'stock' else '/debit')
    id_key = 'productId' if table == 'stock' else 'userId'
    body = lambda op, value=amount: {'operationId': op, id_key: resource, value_key: value}
    call = lambda op, value=amount: request(port, deduct_path, body(op, value))[0]
    compensate = lambda op: request(port, prefix + '/compensate', {'operationId': op})[0]
    balance = lambda: float(request(port, f'{prefix}/{resource}')[1]['quantity' if table == 'stock' else 'availableBalance'])
    try:
        for _ in range(120):
            try:
                if request(port, '/not-found')[0] == 404:
                    break
            except (OSError, urllib.error.URLError):
                pass
            if process.poll() is not None:
                raise AssertionError('Service exited during startup')
            time.sleep(.5)
        else:
            raise AssertionError('Service startup timed out')
        query(f'INSERT INTO {table}({fields}) VALUES({values});')
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            assert list(pool.map(lambda _: call(operation), range(8))) == [204] * 8
        assert balance() == initial - amount, 'duplicate deduction changed resource more than once'
        assert call(operation, amount + 1) == 409, 'same ID with different arguments accepted'
        assert request(port, deduct_path, {'operationId': operation, id_key: resource + 1, value_key: amount})[0] == 409
        assert call(operation + '-invalid', -1) == 400
        assert call(operation + '-large', initial + 1) == 409
        assert query(f"SELECT COUNT(*) FROM {table}_saga_operation WHERE operation_id='{operation}-large';") == '0'
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            assert list(pool.map(lambda _: compensate(operation), range(8))) == [204] * 8
        assert balance() == initial
        assert call(operation) == 409, 'late deduction accepted'
        empty = operation + '-empty'
        assert compensate(empty) == compensate(empty) == 204
        assert call(empty) == 409
        assert balance() == initial, 'empty compensation changed resource'
        # Different operations must also respect the available resource under contention.
        operations = [operation + '-concurrent-' + str(i) for i in range(8)]
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            results = list(pool.map(lambda op: call(op, initial // 2), operations))
        assert results.count(204) == 2 and results.count(409) == 6, results
        assert balance() == 0
        for op in operations:
            assert compensate(op) == 204
        assert balance() == initial
        # A deduction racing with compensation must leave a compensated operation.
        for i in range(5):
            op = operation + '-race-' + str(i)
            with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
                deduction = pool.submit(call, op)
                compensation = pool.submit(compensate, op)
                assert deduction.result() in (204, 409)
                assert compensation.result() == 204
            assert balance() == initial
            assert call(op) == 409
        print(f'PASS {module}: duplicate/concurrent calls, parameter conflicts, rollback, empty compensation, late calls, compensation races, no overdraft')
    except BaseException:
        log.seek(0)
        print(log.read()[-12000:])
        raise
    finally:
        process.terminate()
        process.wait(timeout=20)
        log.close()

if __name__ == '__main__':
    verify('stock-service', 18081, '/stocks', 'product_id', 'quantity', 40, 10,
           'stock-mysql', 'stock_db', 'stock', 'quantity')
    verify('account-service', 18083, '/accounts', 'user_id', 'amount', 40, 10,
           'account-mysql', 'account_db', 'account', 'available_balance')
