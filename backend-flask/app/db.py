import pymysql
from sqlalchemy import create_engine
from .config import DB_CONFIG

def get_conn():
    return pymysql.connect(**DB_CONFIG, cursorclass=pymysql.cursors.DictCursor)

_engine = None

def get_engine():
    """Return a shared SQLAlchemy engine for use with pandas read_sql."""
    global _engine
    if _engine is None:
        url = 'mysql+pymysql://{user}:{password}@{host}:{port}/{database}?charset={charset}'.format(**DB_CONFIG)
        _engine = create_engine(url, pool_recycle=3600)
    return _engine
