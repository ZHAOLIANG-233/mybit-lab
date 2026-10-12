# AI 代码甄别记录

记录实验中用 AI 编程插件辅助时，哪些回答是对的、哪些是错的、我是怎么判断并改掉的。
不是流水账，只挑真正踩到的坑。

---

## 一、@TableLogic 标错字段

**我的提问**：status 表示 1 在职 / 0 离职，能不能直接把 @TableLogic 标在 status 上做逻辑删除？

**AI 回答**：提示了风险，说会和业务状态耦合、建议用独立字段；但**同时**给了"若坚持复用 status，可通过 @TableLogic(value, delval) 指定值"的写法。

**我的判断**：前半段对，后半段容易误导。我实际试了一下：把 @TableLogic 标到 status 上之后，
`selectCount(null)` 从 15 变成 4，`deleteById` 生成的 SQL 变成 `UPDATE emp SET status = 1`（把员工改成在职），
查询和删除的语义整体反转。

**最后怎么改**：迁移到独立的 `del_flag` 字段，`status` 只管在职/离职。这个口径差异后来写进了
`ThreeTrackConsistencyTest`，防止以后再被当成 bug 去"修"。

---

## 二、BaseMapper 的语句什么时候注册的

**我的提问**：`BaseMapper.selectById` 报 `Invalid bound statement (not found)`，
接口确实继承了 BaseMapper、实体注解也写了，为什么？

**AI 回答**：方向对（说是 MyBatis-Plus 在配置初始化阶段动态注入 MappedStatement），
但排查建议默认了"容器自动扫描装配"的场景，把方向带向 mapper 注册 / namespace。

**我的判断**：部分正确。这个工程是**手工装配**的，没有容器扫描。
我用了 `configuration.hasStatement("...EmpPlusMapper.selectById")` 这个不连库的判据，
发现返回 false —— 说明语句压根没注册。

**真正的根因**：装配时用了原生 `Configuration`，而不是 `MybatisConfiguration`。

**最后怎么改**：改用 `MybatisConfiguration` + `MybatisSqlSessionFactoryBuilder`，
12 条通用语句全部注入。这条断言写进了 `DependencyMatrixTest`。

---

## 三、Lombok @Builder 破坏实体反射

**我的提问**：实体加了 @Builder 之后，MyBatis 查询报
`ReflectionException: Error instantiating class ... with invalid types () or values ()`。

**AI 回答**：正确。指出 MyBatis 通过 ObjectFactory 走无参构造反射创建实体，
@Builder 会生成全参构造、挤掉隐式无参构造。

**我的判断**：机制和修复方向都对，可以采纳。

**最后怎么改**：移除 @Builder、恢复 @NoArgsConstructor（与"实体不使用 @Builder"的约定一致），
并补了验证：`getDeclaredConstructors()` 的参数个数从 17 回到 0。

---

## 四、MyBatis 日志接口没有占位符重载

**我的提问**：在拦截器里按 SLF4J 写法写了 `LOG.warn("... {} ...", a, b, c)`，编译报错。

**AI 回答**：正确。`org.apache.ibatis.logging.Log` 只有 `warn(String)` 与 `warn(String, Throwable)`，
没有 SLF4J 的 `{}` 占位符重载。

**我的判断**：API 事实准确，直接采纳。

**最后怎么改**：改成字符串拼接；同时在 finally 里取 SQL 文本时做了容错
（取不到就输出 `<unknown sql>`），避免"打印日志本身抛异常"把真正的 SQL 错误盖住。

---

## 五、我自己额外核对过的几处

这几条 AI 没直接答错，但我没敢照抄：

1. **BaseMapper 注入的语句名单**：我凭印象写了 `selectOne` / `selectMapsPage`，
   实测发现并不存在（实际是 12 条，不含这两个）。说明"方法名"这种事必须实测。
2. **分页插件是否真的注册**：`selectPage` 在插件没注册时**不报错**，只是 total 恒为 0、SQL 不加 LIMIT。
   判据改成了 `getInterceptors().size()`，不连库就能判断。
3. **依赖版本**：AI 给过的版本号里有一个在本地仓库不存在，编译直接失败。
   现在由父 pom 统一仲裁，运行期用 `configuration` 里的真实版本去断言。
4. **凭据**：AI 生成的示例代码里出现过把口令写进配置的写法，一律改掉，
   连接参数只走 `-D` / 环境变量 / 本地 `db.properties`（已 gitignore）。

---

## 六、我用 AI 之前会过的几条

- 依赖坐标和版本能不能在本地仓库解析到？
- 这个 API 真实存在吗（先编译，再运行）？
- 它假设的环境和我这个工程一致吗（手工装配 vs 容器扫描）？
- 涉及语义的改动（逻辑删除、主键策略、插件注册）有没有落到断言上？
- 有没有把口令写进代码或配置文件？

---

**结论**：AI 能很快给出"看起来对"的代码，但根因往往要自己设计判据去验证。
这个实验里最有效的几个判据是：
`configuration.hasStatement(...)`、`getDeclaredConstructors()`、`getInterceptors().size()` ——
都不依赖数据库，跑一次就能证实或证伪。
