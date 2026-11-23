import json
import os
import secrets
import subprocess
import time
from pathlib import Path

root = Path(__file__).resolve().parents[2]
name = "tripee-tests-" + secrets.token_hex(5)
admin_password = secrets.token_hex(24)
app_password = secrets.token_hex(24)

def run(args, **kwargs):
    return subprocess.run(args, check=True, text=True, **kwargs)

try:
    run(["docker", "run", "-d", "--name", name, "--rm", "-p", "127.0.0.1::5432",
         "-e", "POSTGRES_PASSWORD", "-e", "POSTGRES_DB=tripee",
         "postgres:18.6-trixie"], stdout=subprocess.DEVNULL,
        env=os.environ | {"POSTGRES_PASSWORD": admin_password})
    for attempt in range(60):
        if subprocess.run(["docker", "exec", name, "pg_isready", "-U", "postgres"],
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
            break
        time.sleep(1)
    run(["docker", "exec", "-i", name, "psql", "-U", "postgres", "-d", "tripee", "-v", "ON_ERROR_STOP=1"],
        input=(root / "backend/db/schema.sql").read_text(), stdout=subprocess.DEVNULL)
    run(["docker", "exec", "-i", "-e", "TRIPEE_DB_PASSWORD", name,
         "psql", "-U", "postgres", "-d", "tripee", "-v", "ON_ERROR_STOP=1", "-1"],
        input=(root / "infra/provision-backend.sql").read_text(), stdout=subprocess.DEVNULL,
        env=os.environ | {"TRIPEE_DB_PASSWORD": app_password})
    seed = """
        INSERT INTO tripee.admin_account(id,login_id,password_hash,display_name) OVERRIDING SYSTEM VALUE
          VALUES (1,'test-a','test-placeholder','A'),(2,'test-b','test-placeholder','B');
        INSERT INTO tripee.country(id,code,name_ko,name_en) OVERRIDING SYSTEM VALUE VALUES (1,'JP','일본','Japan');
        INSERT INTO tripee.city(id,country_id) OVERRIDING SYSTEM VALUE VALUES (1,1);
        SELECT setval(pg_get_serial_sequence('tripee.city','id'), 1, true);
    """
    run(["docker", "exec", "-i", name, "psql", "-U", "postgres", "-d", "tripee", "-v", "ON_ERROR_STOP=1"],
        input=seed, stdout=subprocess.DEVNULL)
    inspect = json.loads(subprocess.check_output(["docker", "inspect", name], text=True))
    port = inspect[0]["NetworkSettings"]["Ports"]["5432/tcp"][0]["HostPort"]
    env = os.environ | {"POSTGRES_HOST_PORT": port, "TRIPEE_DB_PASSWORD": app_password,
                        "TRIPEE_TEST_ISOLATED_DB": "true", "TRIPEE_TEST_CITY_ID": "1"}
    run(["./gradlew", "postgresTest", "--offline"], cwd=root / "backend", env=env)
finally:
    subprocess.run(["docker", "stop", name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
