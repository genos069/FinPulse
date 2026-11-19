import pytest

from conftest import UnsafeTestDatabase, assert_safe_test_uri


def test_identical_test_and_production_uri_is_refused():
    with pytest.raises(UnsafeTestDatabase, match="identical"):
        assert_safe_test_uri("mongodb+srv://u:p@cluster.mongodb.net/?retryWrites=true", "mongodb+srv://u:p@cluster.mongodb.net/?retryWrites=true")


def test_a_uri_naming_the_production_database_is_refused():
    with pytest.raises(UnsafeTestDatabase, match="production database"):
        assert_safe_test_uri("mongodb://localhost:27017/finpulse", "")
    with pytest.raises(UnsafeTestDatabase):
        assert_safe_test_uri("mongodb://localhost:27017/finpulse?retryWrites=true", "something-else")


def test_a_separate_local_or_test_cluster_is_accepted():
    assert_safe_test_uri("mongodb://localhost:27017", "mongodb+srv://u:p@prod.mongodb.net/finpulse") is None
    assert_safe_test_uri("mongodb://localhost:27017/finpulse_test", "mongodb+srv://u:p@prod.mongodb.net/finpulse") is None


def test_the_real_mongodb_fixture_rejects_the_production_uri_before_connecting(monkeypatch):
    monkeypatch.setenv("MONGODB_URI", "mongodb://prod-host:27017")
    with pytest.raises(UnsafeTestDatabase):
        assert_safe_test_uri("mongodb://prod-host:27017")
