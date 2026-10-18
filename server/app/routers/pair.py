"""配对：手机首次连接时用配对码换取长期 token。"""
from uuid import uuid4

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from .. import db
from ..config import cfg

router = APIRouter(prefix="/api/v1", tags=["pair"])


class PairRequest(BaseModel):
    code: str


@router.post("/pair")
def pair(req: PairRequest):
    if req.code != cfg.pair_code:
        raise HTTPException(status_code=403, detail="配对码错误")
    # 已配对过（如 App 重装）时返回同一个 token，无需重新生成
    token = db.get_setting("paired_token") or uuid4().hex
    db.set_setting("paired_token", token)
    return {"token": token, "server_version": "0.1.0"}
