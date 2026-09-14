# =====================================================================
# 可选: 内置 IK 中文分词插件的 Elasticsearch 镜像
# 架构文档 §6.3 中 patrol-event 索引的 description 字段使用 ik_max_word 分词,
# 官方镜像不含该插件, 索引初始化会报错:
#   analyzer [ik_max_word] not found for field [description]
# 两种解决方式(二选一):
#   1) 构建本镜像 —— 修改 docker-compose.yml 中 elasticsearch 服务:
#      注释掉 image 行, 取消注释 build 段(见 compose 文件内注释)
#   2) 不改镜像, 将索引 mapping 中 description 的 analyzer 临时改为 standard
# 注意: 构建需能访问 GitHub Releases; 网络受限时可先手动下载 zip
#       到 es/ik/ 目录, 改用 RUN bin/elasticsearch-plugin install --batch file:///tmp/ik.zip
# =====================================================================
FROM docker.elastic.co/elasticsearch/elasticsearch:8.11.0

RUN bin/elasticsearch-plugin install --batch \
    https://github.com/infinilabs/analysis-ik/releases/download/v8.11.0/elasticsearch-analysis-ik-8.11.0.zip
