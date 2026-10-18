"""数据同步与备份恢复。

/sync：手机端按 uuid 增量上行（INSERT OR REPLACE，个人单设备场景足够）；
/backup/full：接收全量快照存为 JSON 文件，保留最近 30 份；
/restore：把最新快照原样返回给手机端。
"""
import json
from datetime import datetime
from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException

from .. import db
from ..config import cfg
from ..deps import require_token

router = APIRouter(prefix="/api/v1", tags=["data"], dependencies=[Depends(require_token)])

FIELDS = {
    "categories": ["uuid", "name", "color", "updated_at"],
    "time_records": ["uuid", "category_uuid", "start_at", "end_at", "note", "updated_at"],
    "daily_logs": ["uuid", "date", "content", "updated_at"],
    "log_images": ["uuid", "log_uuid", "remote_id", "updated_at"],
    "todos": ["uuid", "content", "due_at", "remind_at", "status", "done_at", "updated_at"],
}


@router.post("/sync")
def sync(payload: dict):
    pairs: list[tuple[str, list]] = []
    counts: dict[str, int] = {}
    for table, fields in FIELDS.items():
        rows = payload.get(table) or []
        n = 0
        for row in rows:
            if not isinstance(row, dict) or not row.get("uuid"):
                continue
            cols = ",".join(fields)
            marks = ",".join("?" * len(fields))
            pairs.append((
                f"INSERT OR REPLACE INTO {table}({cols}) VALUES({marks})",
                [row.get(f) for f in fields],
            ))
            n += 1
        counts[table] = n
    db.execute_many(pairs)
    return {"ok": True, "counts": counts}


def _backups_dir() -> Path:
    d = cfg.data_dir / "backups"
    d.mkdir(parents=True, exist_ok=True)
    return d


def _all_backups() -> list[Path]:
    # 文件名含时间戳，按名排序即按时间排序
    return sorted(_backups_dir().glob("backup_*.json"))


@router.post("/backup/full")
def backup_full(payload: dict):
    ts = datetime.now()
    name = f"backup_{ts.strftime('%Y%m%d_%H%M%S')}.json"
    (_backups_dir() / name).write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    for old in _all_backups()[:-30]:
        old.unlink(missing_ok=True)
    return {"ok": True, "file": name, "ts": ts.isoformat(timespec="seconds")}


@router.get("/backup/list")
def backup_list():
    items = [
        {"file": p.name, "size": p.stat().st_size}
        for p in reversed(_all_backups())
    ]
    return {"items": items}


@router.get("/backup/latest")
def backup_latest():
    backups = _all_backups()
    if not backups:
        raise HTTPException(status_code=404, detail="服务器还没有任何备份")
    p = backups[-1]
    return {"file": p.name, "size": p.stat().st_size}


@router.post("/restore")
def restore():
    backups = _all_backups()
    if not backups:
        raise HTTPException(status_code=404, detail="服务器还没有任何备份")
    p = backups[-1]
    return {"file": p.name, "data": json.loads(p.read_text(encoding="utf-8"))}
