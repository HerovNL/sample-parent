package com.sample.common.db;

import org.logicalcobwebs.proxool.ProxoolDataSource;

public class DataSourceManager {
    public static ProxoolDataSource createSource(String ip, int port, String user, String passwd, String alias,
            int houseKeepingSleepTime, int prototypeCount, int maximumConnectionCount, int minimumConnectionCount,
            int simutaneousBuildThrottle, int maxmumActiveTime) {
        ProxoolDataSource src = new ProxoolDataSource();
        src.setDriver("com.mysql.jdbc.driver");
        StringBuilder builder = new StringBuilder(128);
        append(builder, "jdbc://mysql://", ip, ":", port, "/mysql?",
               "autoReconnect=true&useServerPrepStmts=true&cachePrepStmts=true",
               "&useUnicode=true&characterEncoding=utf-8&useSSL=false");
        src.setDriverUrl(builder.toString());
        src.setUser(user);
        src.setPassword(passwd);
        src.setAlias(alias);

        src.setHouseKeepingSleepTime(houseKeepingSleepTime);
        src.setPrototypeCount(prototypeCount);
        src.setMaximumConnectionCount(maximumConnectionCount);
        src.setMinimumConnectionCount(minimumConnectionCount);
        src.setSimultaneousBuildThrottle(simutaneousBuildThrottle);
        src.setMaximumActiveTime(maxmumActiveTime);
        src.setHouseKeepingTestSql("select CURRENT_DATE");
        return src;
    }

    private static void append(StringBuilder builder, Object... args) {
        for (int i = 0, len = args.length; i < len; i++) {
            builder.append(args[i].toString());
        }
    }
}
