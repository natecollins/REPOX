package pt.utl.ist.repox.database;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.dbcp.BasicDataSourceFactory;
import org.apache.log4j.Logger;

import pt.utl.ist.repox.accessPoint.AccessPoint;
import pt.utl.ist.repox.configuration.RepoxConfiguration;
import pt.utl.ist.repox.dataProvider.DataSource;
import pt.utl.ist.repox.util.sql.SqlUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.Properties;

/**
 */
public class DerbyDatabaseAccess implements DatabaseAccess {
    private static final Logger  log = Logger.getLogger(DerbyDatabaseAccess.class);

    protected RepoxConfiguration configuration;
    protected BasicDataSource    repoxDataSource;
    protected String             dbUrl;

    /**
     * Creates a new instance of this class.
     * 
     * @param configuration
     */
    public DerbyDatabaseAccess(RepoxConfiguration configuration) {
        super();

        try {
            this.configuration = configuration;

            Properties dbConnectionProperties = new Properties();
            String url = configuration.getDatabaseUrl() + configuration.getDatabasePath();
            if (configuration.isDatabaseCreate()) {
                url += ";create=true";
            }
            dbConnectionProperties.put("url", url);
            dbConnectionProperties.put("driverClassName", configuration.getDatabaseDriverClassName());

            log.info("Database URL connection: " + url);
            repoxDataSource = (BasicDataSource)BasicDataSourceFactory.createDataSource(dbConnectionProperties);

            Class.forName(configuration.getDatabaseDriverClassName()).newInstance();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public String getVarType(Class classOfValue) {
        String valueType = "varchar(255)";

        if (classOfValue.equals(Date.class)) {
            valueType = "date";
        } else if (classOfValue.equals(Integer.class)) {
            valueType = "int";
        } else if (classOfValue.equals(Long.class)) {
            valueType = "bigint";
        } else if (classOfValue.equals(byte[].class)) {
            valueType = "blob(16M)";
        }

        return valueType;
    }

    @Override
    public boolean checkTableExists(String table, Connection con) {
        try {
            SqlUtil.getSingleValue("select * from " + table + " fetch first 1 rows only", con);
        } catch (Exception e) {
            return false;
        }

        return true;
    }

    @Override
    public Connection openDbConnection() {
        try {
            return repoxDataSource.getConnection();
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    @Override
    public void createTableIndexes(Connection con, String idType, String table, String valueType, boolean indexValue) {
        String createTableQuery = "CREATE TABLE " + table + " (id int NOT NULL GENERATED BY DEFAULT AS IDENTITY, " + "nc " + idType + " NOT NULL, " + "value " + valueType + ", deleted SMALLINT, PRIMARY KEY(id))";
        log.info(createTableQuery);
        SqlUtil.runUpdate(createTableQuery, con);

        String iSystemIndexQuery = "CREATE INDEX " + table + "_i_nc ON " + table + "(nc)";
        SqlUtil.runUpdate(iSystemIndexQuery, con);

        if (indexValue) {
            String valueIndexQuery = "CREATE INDEX " + table + "_i_val ON " + table + "(value)";
            SqlUtil.runUpdate(valueIndexQuery, con);
        }
    }

    @Override
    public void deleteTable(Connection con, String table) throws SQLException {
        PreparedStatement statement = con.prepareStatement("drop table " + table);
        SqlUtil.runUpdate(statement);
    }

    @Override
    public String renameTableString(String oldTableName, String newTableName) {
        return "RENAME TABLE " + oldTableName + " TO " + newTableName;
    }

    @Override
    public void renameIndexString(Connection con, String newTableName, String oldTableName, boolean indexValue) {
        String iSystemRenameIndexQuery = "RENAME INDEX " + oldTableName + "_i_nc TO " + newTableName + "_i_nc";
        SqlUtil.runUpdate(iSystemRenameIndexQuery, con);

        if (indexValue) {
            iSystemRenameIndexQuery = "RENAME INDEX " + oldTableName + "_i_val TO " + newTableName + "_i_val";
            SqlUtil.runUpdate(iSystemRenameIndexQuery, con);
        }
    }

    @Override
    public String getHeaderAndRecordQuery(DataSource dataSource, String fromDateString, String toDateString, Integer offset, Integer numberResults, boolean retrieveFullRecord) {

        String recordTable = (AccessPoint.PREFIX_INTERNAL_BD + dataSource.getId() + AccessPoint.SUFIX_RECORD_INTERNAL_BD).toLowerCase();
        String timestampTable = (AccessPoint.PREFIX_INTERNAL_BD + dataSource.getId() + AccessPoint.SUFIX_TIMESTAMP_INTERNAL_BD).toLowerCase();

        if (offset == null || offset < 0) offset = 0;
        boolean noResultLimit = (numberResults == null || numberResults <= 0);

        String query = "select " + recordTable + ".nc, " + timestampTable + ".deleted" + ", " + timestampTable + ".value" + ", " + recordTable + ".id";
        if (retrieveFullRecord) {
            query += ", " + recordTable + ".value";
        }

        query += " from " + recordTable + ", " + timestampTable + " where " + recordTable + ".nc = " + timestampTable + ".nc";

        if (fromDateString != null || toDateString != null) {
            if (fromDateString != null) {
                query += " and " + timestampTable + ".value >= '" + fromDateString + "'";
            }
            if (toDateString != null) {
                query += " and " + timestampTable + ".value <= '" + toDateString + "'";
            }
        }

        query += " and " + recordTable + ".id > " + offset + " order by " + recordTable + ".id";

        return query;
    }

    @Override
    public String getFieldQuery(DataSource dataSource, String fromDateString, String toDateString, Integer offset, Integer numberResults, String field) {

        String recordTable = (AccessPoint.PREFIX_INTERNAL_BD + dataSource.getId() + AccessPoint.SUFIX_RECORD_INTERNAL_BD).toLowerCase();
        String timestampTable = (AccessPoint.PREFIX_INTERNAL_BD + dataSource.getId() + AccessPoint.SUFIX_TIMESTAMP_INTERNAL_BD).toLowerCase();

        String query = "select " + recordTable.toLowerCase() + "." + field + " from " + recordTable.toLowerCase();
        if (fromDateString != null || toDateString != null) {
            query += ", " + timestampTable.toLowerCase() + " where " + recordTable.toLowerCase() + ".nc = " + timestampTable.toLowerCase() + ".nc";
            if (fromDateString != null) {
                query += " and " + timestampTable.toLowerCase() + ".value >= '" + fromDateString + "'";
            }
            if (toDateString != null) {
                query += " and " + timestampTable.toLowerCase() + ".value <= '" + toDateString + "'";
            }
        }

        return query;
    }
}
