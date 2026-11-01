from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool
from app.api import create_app
from app.providers.config import ExperimentConfigFactory
from app.settings import Settings


def test_empty_session_list_is_success(client):
    response = client.get('/api/sessions')
    assert response.status_code == 200
    assert response.json() == []


def test_uninitialized_schema_is_explicit_and_is_not_created_or_hidden():
    # Isolated in-memory test database. No production database or Alembic invocation.
    engine = create_engine('sqlite://', poolclass=StaticPool,
                           connect_args={'check_same_thread': False})
    factory = ExperimentConfigFactory(Settings(_env_file=None, suggestion_provider='mock'))
    try:
        with TestClient(create_app(sessionmaker(engine), factory)) as api:
            response = api.get('/api/sessions')
        assert response.status_code == 503
        assert response.json()['code'] == 'F4_SCHEMA_NOT_READY'
        assert 'SELECT' not in response.text
        from sqlalchemy import inspect
        assert inspect(engine).get_table_names() == []
    finally:
        engine.dispose()
