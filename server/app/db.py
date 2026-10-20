"""SQLite 存储与表结构。

服务器作为备份端：业务表按 uuid 镜像手机端数据，
另有 agent_calls（AI 调用与花费流水）和 settings（配对 token 等）。
"""
import sqlite3
import threading

from .config import cfg

_lock = threading.Lock()
_conn: sqlite3.Connection | None = None

SCHEMA = """
CREATE TABLE IF NOT EXISTS folders(
    uuid TEXT PRIMARY KEY, name TEXT NOT NULL, created_at TEXT);
CREATE TABLE IF NOT EXISTS categories(
    uuid TEXT PRIMARY KEY, name TEXT NOT NULL, color TEXT, updated_at TEXT);
CREATE TABLE IF NOT EXISTS time_records(
    uuid TEXT PRIMARY KEY, category_uuid TEXT, start_at TEXT NOT NULL,
    end_at TEXT, note TEXT, updated_at TEXT);
CREATE TABLE IF NOT EXISTS daily_logs(
    uuid TEXT PRIMARY KEY, date TEXT NOT NULL, content TEXT DEFAULT '',
    updated_at TEXT, folder_uuid TEXT);
CREATE TABLE IF NOT EXISTS log_images(
    uuid TEXT PRIMARY KEY, log_uuid TEXT, remote_id TEXT, updated_at TEXT);
CREATE TABLE IF NOT EXISTS todos(
    uuid TEXT PRIMARY KEY, content TEXT NOT NULL, due_at TEXT, remind_at TEXT,
    status TEXT DEFAULT 'pending', done_at TEXT, updated_at TEXT, folder_uuid TEXT);
CREATE TABLE IF NOT EXISTS agent_calls(
    id INTEGER PRIMARY KEY AUTOINCREMENT, ts TEXT NOT NULL, model TEXT,
    prompt_tokens INTEGER, completion_tokens INTEGER, cost REAL DEFAULT 0, purpose TEXT);
CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY, value TEXT);
"""

# 旧库补列（v0.9.0 新增 folder_uuid，已存在则忽略报错）
ALTERATIONS = [
    "ALTER TABLE daily_logs ADD COLUMN folder_uuid TEXT",
    "ALTER TABLE todos ADD COLUMN folder_uuid TEXT",
]


def init_db() -> None:
    global _conn
    cfg.data_dir.mkdir(parents=True, exist_ok=True)
    (cfg.data_dir / "images").mkdir(exist_ok=True)
    (cfg.data_dir / "backups").mkdir(exist_ok=True)
    _conn = sqlite3.connect(cfg.data_dir / "timemaster.db", check_same_thread=False)
    _conn.row_factory = sqlite3.Row
    with _lock:
        _conn.executescript(SCHEMA)
        for stmt in ALTERATIONS:
            try:
                _conn.execute(stmt)
            except sqlite3.OperationalError:
                pass  # 列已存在
        _conn.commit()


def conn() -> sqlite3.Connection:
    assert _conn is not None, "数据库未初始化，请先调用 init_db()"
    return _conn


def execute_many(pairs: list[tuple[str, list]]) -> None:
    """在锁内执行一批写操作并提交，保证多线程下的串行写入。"""
    with _lock:
        for sql, params in pairs:
            conn().execute(sql, params)
        conn().commit()


def get_setting(key: str) -> str | None:
    row = conn().execute("SELECT value FROM settings WHERE key=?", (key,)).fetchone()
    return row["value"] if row else None


def set_setting(key: str, value: str) -> None:
    execute_many([
        ("INSERT INTO settings(key,value) VALUES(?,?) "
         "ON CONFLICT(key) DO UPDATE SET value=excluded.value", [key, value]),
    ])
