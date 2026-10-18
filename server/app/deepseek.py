"""DeepSeek 官方 API 封装（OpenAI 兼容 /chat/completions）。

Prompt 强约束输出简短：≤150 字，3 条要点 + 2 个自测问题。
"""
import httpx

SYSTEM_PROMPT = (
    "你是一个帮助用户复习巩固的中文学习助手。要求："
    "1.输出必须非常简短，总共不超过150字；"
    "2.格式固定为：先输出最多3条【要点】回顾，再输出2个【自测】问题；"
    "3.不要客套话，不要大段重复原文。"
)


def chat_review(cfg, text: str) -> tuple[str, dict]:
    if not cfg.deepseek_api_key:
        raise RuntimeError("服务器未配置 DEEPSEEK_API_KEY")
    payload = {
        "model": cfg.deepseek_model,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": text[:4000]},
        ],
        "max_tokens": 400,
        "temperature": 0.5,
        "stream": False,
    }
    try:
        resp = httpx.post(
            f"{cfg.deepseek_base_url}/chat/completions",
            headers={"Authorization": f"Bearer {cfg.deepseek_api_key}"},
            json=payload,
            timeout=60,
        )
    except httpx.HTTPError as e:
        raise RuntimeError(f"无法连接 DeepSeek：{e}")
    if resp.status_code != 200:
        raise RuntimeError(f"DeepSeek 返回 {resp.status_code}：{resp.text[:200]}")
    data = resp.json()
    try:
        content = data["choices"][0]["message"]["content"]
        usage = data["usage"]
    except (KeyError, IndexError) as e:
        raise RuntimeError(f"DeepSeek 响应格式异常：{e}")
    return content, {
        "model": data.get("model", cfg.deepseek_model),
        "prompt_tokens": usage.get("prompt_tokens", 0),
        "completion_tokens": usage.get("completion_tokens", 0),
    }
