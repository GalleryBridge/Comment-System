# 代码使用说明
项目代码包含2个分支：
- master : 主分支，包含完整功能代码
- dev : 开发环境分支，负责测试和编写功能模块的分支
切换分支
```git
git checkout init
```
## 项目架构

![image-20230610113419807](G:\Project\Commont\Comment-System\hm-dianping-init\README.assets\image-20230610113419807.png)

## 短信登录

### 集群Session的共享问题
多台Tomcat并不共享Session存储空间, 当请求切换到不同的Tomcat服务器时会导致数据丢失
Session的替代方案应该满足
- 数据共享
- 内存存储
- key、 value结构

所以将验证信息保存在Redis中 以Token为key值 user对象为value