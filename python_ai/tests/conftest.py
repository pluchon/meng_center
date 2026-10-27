import os

# 离线测试不向第三方追踪平台发送资料。
os.environ["LANGSMITH_TRACING"] = "false"
os.environ["LANGCHAIN_TRACING_V2"] = "false"
