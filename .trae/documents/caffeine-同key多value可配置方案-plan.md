# Caffeine 同 Key 多 Value 可配置方案（Plan）

## 1. 目标与范围

* 目标：让当前基于 Caffeine 的 Flow 存储支持“同一个 key 下缓存多个 value”，并保持现有单值行为默认不变。

* 范围：仅改造 `template-flow` 模块中的 `CaffeineFlowStorage` 路径；`QUEUE` 存储实现不做行为变更。

* 非目标：不引入分布式多值缓存；不改变 `FlowJoiner.joinKey()` 的签名。

## 2. 现状与约束

* 当前 Caffeine 存储类型是 `Cache<String, FlowEntry<T>>`，天然一 key 一 value。

* 覆盖模式（`needMatched=false`）下同 key 通过 `put` 覆盖旧值并触发 `REPLACE` 失败流。

* 配对模式（`needMatched=true`）下同 key 通过 `compute` 进行“一进一出”配对。

* `FlowStorage.deposit()` 负责引用计数和失败回滚，存储层需维持其调用契约。

## 3. 总体设计

### 3.1 新增可配置项

* 在 `TemplateConfigProperties.Flow.PerJob` 增加以下字段：

  * `boolean multiValueEnabled`：是否开启同 key 多 value（默认 `false`）。

  * `int multiValueMaxPerKey`：单 key 最大 value 数（默认 `1`；开启后建议默认 `16`，最终以默认值设计为准）。

  * `enum multiValueOverflowPolicy`：超限策略，建议两种：`DROP_OLDEST`、`DROP_NEWEST`（默认 `DROP_OLDEST`）。

* 校验规则：

  * `multiValueMaxPerKey > 0`。

  * 当 `multiValueEnabled=false` 时，忽略 `multiValue*` 细项并保留单值语义。

### 3.2 存储结构抽象升级

* 将 `CaffeineFlowStorage` 的缓存 value 从 `FlowEntry<T>` 抽象为“槽位”结构：

  * 单值模式：槽位内仅一个元素（行为与现状一致）。

  * 多值模式：槽位内为有界双端队列（`Deque<FlowEntry<T>>`）。

* 并发策略：

  * 统一通过 `asMap().compute(key, ...)` 修改槽位，确保同 key 下原子更新。

  * 避免先读后写导致的竞态。

### 3.3 行为语义（关键）

* 覆盖模式（`needMatched=false`）：

  * 单值模式：维持现状（新值替换旧值，旧值走 `REPLACE` 失败流）。

  * 多值模式：新值追加到队尾；超限后按 `overflowPolicy` 处理被淘汰项，并对淘汰项执行失败回调（原因沿用 `REPLACE` 或新增更细原因，优先复用已有原因减少影响）。

* 配对模式（`needMatched=true`）：

  * 单值模式：维持现状（一进一出）。

  * 多值模式：到达新数据时，优先弹出队首最老等待项与当前项配对；若无等待项，则当前项入队等待。

  * 配对失败回写：若 `isMatched(partner, entry)=false`，则将 `partner` 与 `entry` 按原先先后顺序重新写回同 key 槽位（partner 在前、entry 在后），再执行超限裁剪策略；不立即按 `MISMATCH` 失败出库。

  * 语义定义为 FIFO 配对，避免随机性。

### 3.4 过期与驱逐处理

* Caffeine 驱逐触发时，`removalListener` 需要支持“槽位内多个 entry”的批处理流程：

  * 先对被驱逐槽位执行一次“内部配对尝试”（按 FIFO 取前两条做一次匹配）。

  * 若该次匹配成功，则这两条按成功路径出库；其余未参与或未成功配对的条目再走现有 `onEntryRemoved/handleEgress` 被动失败流程。

  * 若该次匹配失败，则两条仍按驱逐原因走被动失败（不回写，因已驱逐）。

* 要求：单次 key 驱逐不丢任何 entry 的失败/出库处理。

### 3.5 指标与日志

* 复用现有日志风格，新增最小必要字段：`multiValueEnabled`、`queueSize`、`overflowPolicy`（仅在关键路径 debug 级别输出）。

* 指标保持兼容：

  * 现有存量指标仍按 key 统计。

  * 新增必选指标：多值模式下“丢弃或覆盖”计数器（建议名 `storage_multi_value_discard_total`），并打上 `jobId`、`reason`（`overflow_drop_oldest` / `overflow_drop_newest` / `replace`）标签。

  * 新增可选指标：每 key 平均槽位长度、overflow 次数。

## 4. 兼容性策略

* 默认配置 `multiValueEnabled=false`，升级后行为零变化。

* 老配置文件不需要变更即可启动。

* 对上层 `FlowJoiner`、`FlowLauncher` API 无破坏性修改。

## 5. 实施步骤（可直接执行）

1. **属性扩展**：修改 `TemplateConfigProperties.Flow.PerJob` 新增 `multiValue*` 配置及校验；补齐默认值与启动日志。
2. **模型抽象**：在 `template-flow` 内引入槽位模型（如内部静态类），封装单值/多值操作（append、poll、evict）。
3. **写入路径改造**：重写 `CaffeineFlowStorage.doDeposit()` 的覆盖/配对逻辑，统一改为 `compute` 原子更新，并实现“配对失败双条回写”。
4. **驱逐路径改造**：调整 `removalListener` 与 `onEntryRemoved`，实现“驱逐槽位先做一次内部配对，再处理剩余条目”。
5. **指标补齐**：新增“多值丢弃/覆盖计数器”，并统一 reason 标签字典。
6. **故障语义对齐**：保证 overflow 被淘汰项、替换项、TTL 驱逐项均走现有失败回调链路，避免引用计数泄漏。
7. **配置接线**：在 `CaffeineFlowStorage` 构造阶段读取 per-job 新配置并生效。
8. **测试补齐**：新增/调整单元测试与集成测试覆盖单值兼容、多值 FIFO、配对失败回写、超限策略、驱逐前一次配对、并发正确性。
9. **文档更新**：更新 flow 配置文档，补充多值模式语义、风险与建议参数。

## 6. 测试设计

* 单值回归：`multiValueEnabled=false` 下行为与当前一致（覆盖替换、配对一进一出、TTL 驱逐）。

* 多值基础：同 key 连续写入 N 条，未匹配前缓存长度增长到 N。

* 配对 FIFO：同 key 预存 A/B/C，后续到达 X/Y，应形成 `(A,X) (B,Y)`，剩余 `C`。

* 配对失败回写：同 key 预存 A，后续到达 X 且 `isMatched(A,X)=false`，则缓存中仍保留 `A,X`（顺序不变），并触发超限裁剪校验。

* 超限策略：

  * `DROP_OLDEST`：超过上限时淘汰最老项，并触发失败回调。

  * `DROP_NEWEST`：新入项被丢弃并触发失败回调，已有队列不变。

* 驱逐前一次配对：TTL/容量驱逐包含多条时，先尝试一次内部配对；验证成功分支与失败分支均符合定义，剩余条目无丢回调。

* 指标校验：多值丢弃/覆盖场景下 `storage_multi_value_discard_total` 按 reason 正确累加。

* 并发正确性：同 key 高并发 deposit 下不出现重复配对、漏配对、负引用计数。

## 7. 风险与规避

* 风险：多值模式下单 key 内存占用上升。规避：`multiValueMaxPerKey` 强约束 + `maximumSize` 总量上限。

* 风险：驱逐回调放大导致瞬时处理抖动。规避：批量回调中保持轻量，必要时异步化后续处理。

* 风险：失败原因语义变化影响下游统计。规避：先复用既有原因并在文档中明确映射。

## 8. 交付验收标准

* 配置关闭时，现网行为与指标无可见差异。

* 配置开启时，同 key 支持多 value，且配对/覆盖/驱逐行为与本文定义一致。

* 全量测试通过，关键并发场景无回归。

