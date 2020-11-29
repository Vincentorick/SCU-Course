# SCU Course 开发日志

### Spring Boot

#### 约定大于配置！

resources下的静态资源目录包括：/**, /public, /static, /resources

在templates目录下的所有页面，只能通过controller来跳转

当需要引入新的jar包时，在pom.xml里面添加新的dependency就可以了