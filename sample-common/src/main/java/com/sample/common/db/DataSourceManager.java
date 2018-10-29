package com.sample.common.db;


import com.alibaba.druid.pool.DruidDataSource;
import com.zaxxer.hikari.HikariDataSource;

public class DataSourceManager {
    // Druid configuration reference: https://github.com/alibaba/druid/wiki/%E5%B8%B8%E8%A7%81%E9%97%AE%E9%A2%98
    public static DruidDataSource createSource(String name, String jdbcUrl, String userName, String password,
            int maxActive, int minIdle, int maxWait, int scanInterval, int minActiveTime) {
        DruidDataSource src = new DruidDataSource();
        src.setName(name);
        // Eg:
        // jdbc:mysql://10.1.1.2:3306/mysql?autoReconnect=true&failOverReadOnly=false&useServerPrepStmts=true
        // &cachePrepStmts=true&useUnicode=true&characterEncoding=utf-8&useSSL=false
        src.setUrl(jdbcUrl);
        src.setUsername(userName);
        src.setPassword(password);
        src.setDriverClassName("com.mysql.jdbc.Driver"); // Eg: here is for MySQL

        src.setInitialSize(minIdle);
        src.setMaxActive(maxActive);
        src.setMinIdle(minIdle);
        src.setMaxWait(maxWait); // Eg: 30000ms Timeout for client to get connection from pool
        src.setTimeBetweenEvictionRunsMillis(scanInterval); // Eg: 60000ms Interval to scan failed connections
        src.setMinEvictableIdleTimeMillis(minActiveTime);   // Eg: 300000ms Minimum timeout for connection alive

        src.setTestWhileIdle(true); // If true, will check validity when connection in idle more than scanInterval
        src.setTestOnBorrow(true);  // If true, check connection before assignment, but will affect performance
        src.setTestOnReturn(false);
        src.setPoolPreparedStatements(false); // If true, will pool prepared statement(suggested true for oracle)
        src.setMaxPoolPreparedStatementPerConnectionSize(20); // If pool prepared statement, size for each connection
        src.setValidationQuery("select 1"); // SQL to check connection, such as "select 1" or "select 'x'"

        src.setRemoveAbandoned(true); // If true, will close connections forgotten to return
        src.setRemoveAbandonedTimeout(1800); //Eg: 1800s Timeout to close connections forgotten to return

        src.setKeepAlive(true); // If true, automatically keep alive connections at least of minIdle

        return src;
    }

    public static HikariDataSource createSource(String name,String jdbcUrl,String userName,String password,
            int connectionTimeout,int idleTimeout,int maxLifeTime){
        HikariDataSource src = new HikariDataSource();
        src.setPoolName(name);
        src.setJdbcUrl(jdbcUrl);
        src.setUsername(userName);
        src.setPassword(password);
        src.setDriverClassName("com.mysql.jdbc.Driver"); // Eg: here is for MySQL

        src.setConnectionTimeout(connectionTimeout); // Eg: 30000ms Timeout for client to get connection from pool
        src.setIdleTimeout(idleTimeout); // Eg: 60000ms If connection idle timeout, it will be removed from pool
        src.setMaxLifetime(maxLifeTime); // Eg: 1800000ms=30min Unused connection timeout, it will be removed from pool


        return src;
    }
    private static void append(StringBuilder builder, Object... args) {
        for (int i = 0, len = args.length; i < len; i++) {
            Object arg = args[i];
            if (arg instanceof String) {
                builder.append((String) arg);
            } else {
                builder.append(arg.toString());
            }
        }
    }
}
