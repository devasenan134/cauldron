import os
from pathlib import Path

from sqlalchemy import event, inspect, text
from sqlmodel import Session, SQLModel, create_engine

DB_PATH = Path(os.environ.get("CAULDRON_DB", Path(__file__).resolve().parents[2] / "data" / "cauldron.db"))
engine = create_engine(f"sqlite:///{DB_PATH}", connect_args={"check_same_thread": False})


@event.listens_for(engine, "connect")
def _sqlite_pragmas(conn, _):
    cur = conn.cursor()
    cur.execute("PRAGMA foreign_keys=ON")
    cur.execute("PRAGMA journal_mode=WAL")
    cur.close()


def init_db() -> None:
    from . import models  # noqa: F401  (register tables)

    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    SQLModel.metadata.create_all(engine)
    add_missing_columns()


def add_missing_columns() -> None:
    """create_all() makes new tables but never alters old ones; add any new columns.

    Enough for additive changes (a nullable column, or one with a scalar default).
    """
    existing = inspect(engine)
    with engine.begin() as conn:
        for table in SQLModel.metadata.sorted_tables:
            have = {c["name"] for c in existing.get_columns(table.name)}
            for column in table.columns:
                if column.name in have:
                    continue
                ddl = f'ALTER TABLE "{table.name}" ADD COLUMN "{column.name}" {column.type.compile(engine.dialect)}'
                default = column.default.arg if column.default is not None and column.default.is_scalar else None
                if default is not None:
                    ddl += f" NOT NULL DEFAULT {default!r}"
                for fk in column.foreign_keys:
                    ddl += f" REFERENCES {fk.column.table.name}({fk.column.name})"
                    if fk.ondelete:
                        ddl += f" ON DELETE {fk.ondelete}"
                conn.execute(text(ddl))


def get_session():
    with Session(engine) as session:
        yield session
