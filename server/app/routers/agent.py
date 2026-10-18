"""DeepSeek 代理（复习巩固）与花费统计。"""
from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

from .. import db
from ..config import cfg
from ..deepseek import chat_review
from ..deps import require_token

router = APIRouter(prefix="/api/v1", tags=["agent"], dependencies=[Depends(require_token)])


class ReviewRequest(BaseModel):
    text: str
    purpose: str = "daily_review"


@router.post("/agent/review")
def review(req: ReviewRequest):
    text = req.text.strip()
    if not text:
        raise HTTPException(status_code=400, detail="复习内容不能为空")
    try:
        content, usage = chat_review(cfg, text)
    except RuntimeError as e:
        raise HTTPException(status_code=502, detail=str(e))
    cost = (usage["prompt_tokens"] / 1e6 * cfg.price_prompt_per_m
            + usage["completion_tokens"] / 1e6 * cfg.price_completion_per_m)
    db.execute_many([
        ("INSERT INTO agent_calls(ts,model,prompt_tokens,completion_tokens,cost,purpose) "
         "VALUES(?,?,?,?,?,?)",
         [datetime.now().isoformat(timespec="seconds"), usage["model"],
          usage["prompt_tokens"], usage["completion_tokens"], round(cost, 6), req.purpose]),
    ])
    return {"content": content, "model": usage["model"], "cost": round(cost, 6), "usage": usage}


@router.get("/agent/spending")
def spending():
    c = db.conn()
    today = datetime.now().strftime("%Y-%m-%d")
    row = c.execute("SELECT COUNT(*) AS n, IFNULL(SUM(cost),0) AS total FROM agent_calls").fetchone()
    today_row = c.execute(
        "SELECT IFNULL(SUM(cost),0) AS v FROM agent_calls WHERE substr(ts,1,10)=?", (today,)
    ).fetchone()
    month_row = c.execute(
        "SELECT IFNULL(SUM(cost),0) AS v FROM agent_calls WHERE substr(ts,1,7)=?", (today[:7],)
    ).fetchone()
    items = [dict(r) for r in c.execute(
        "SELECT id,ts,model,prompt_tokens,completion_tokens,cost,purpose "
        "FROM agent_calls ORDER BY id DESC LIMIT 50").fetchall()]
    return {
        "calls_total": row["n"],
        "total_cost": round(row["total"], 6),
        "today_cost": round(today_row["v"], 6),
        "month_cost": round(month_row["v"], 6),
        "price_prompt_per_m": cfg.price_prompt_per_m,
        "price_completion_per_m": cfg.price_completion_per_m,
        "items": items,
    }
