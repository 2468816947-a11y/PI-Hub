# =====================================================================
# 内置 IK 中文分词插件的 Elasticsearch 镜像
# 架构文档 §6.3 中 patrol-event 索引的 description 字段使用 ik_max_word 分词,
# 官方镜像不含该插件, 索引初始化会报错:
#   analyzer [ik_max_word] not found for field [description]
# 插件 zip 已本地下载至 es/elasticsearch-analysis-ik-8.11.0.zip(2026-09-15),
# 构建时从本地文件安装, 无需联网; 若升级 ES 版本需重新下载对应版本 zip
# =====================================================================
FROM docker.elastic.co/elasticsearch/elasticsearch:8.11.0

COPY --chown=elasticsearch:root elasticsearch-analysis-ik-8.11.0.zip /tmp/ik.zip
RUN bin/elasticsearch-plugin install --batch file:///tmp/ik.zip \
    && rm -f /tmp/ik.zip
