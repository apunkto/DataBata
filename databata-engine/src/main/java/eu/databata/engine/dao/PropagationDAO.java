package eu.databata.engine.dao;

import eu.databata.engine.util.PropagationUtils;

import eu.databata.engine.model.HistoryLogEntry;
import eu.databata.engine.model.PropagationObject;
import eu.databata.engine.model.PropagationObject.ObjectType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.support.JdbcDaoSupport;

/**
 * @author Maksim Boiko <mailto:max@webmedia.ee>
 */
public class PropagationDAO extends JdbcDaoSupport {
  private static final Logger LOG = Logger.getLogger(PropagationDAO.class);
  private static final String TABLE_NAME_MARKER = "<<TABLE_NAME>>";
  private final static int MAX_SQLTEXT_LENGTH = 2000;
  private final static int MAX_ERRORTEXT_LENGTH = 1000;

  private String changeHistoryTable = "SYS_DB_PROPAGATOR_HISTORY";
  private String propagationObjectsTable = "SYS_DB_PROPAGATOR_OBJECT";
  private String lockTable = "SYS_DB_PROPAGATOR_LOCK";
  private String historyLogTable = "SYS_DB_PROPAGATOR_SQL_LOG";
  private String databaseCode = "ORA";

  private String createHistorySQL;
  private String createPropagationObjectsSQL;
  private String createLockSQL;
  private String createHistoryLogSQL;

  /**
   * This method loads already propagated changes from the DB. If history table does not exist then empty set is
   * returned and new history table created.
   */
  public Set<String> getPropagatedChages(String moduleName) {
    try {
      return loadChangeHistory(moduleName);
    } catch (final BadSqlGrammarException ex) {
      LOG.info("No history table found, creating it.");
      createHistoryTable();
      return new HashSet<String>();
    }
  }

  private void createHistoryTable() {
    getJdbcTemplate().update(getCreateHistorySQL());
  }

  private Set<String> loadChangeHistory(String moduleName) {
    return new HashSet<String>(getJdbcTemplate().queryForList("SELECT code FROM " + changeHistoryTable
                                                                  + " WHERE module_name = ?",
                                                              new Object[] { moduleName },
                                                              String.class));
  }

  public void updateHistoryTable(String moduleName, String id) {
    getJdbcTemplate().update("INSERT INTO " + changeHistoryTable + " VALUES (?, ?, ?)",
                             new Object[] { moduleName, id, new Date() });
  }

  public List<PropagationObject> getPropagationObjects(String moduleName) {
    String sql = "SELECT * FROM " + propagationObjectsTable + " WHERE object_type IN (?,?,?,?,?,?) AND module_name = ?";
    if (LOG.isDebugEnabled()) {
      LOG.debug("Loading propagation objects: " + sql);
    }

    try {
      return getJdbcTemplate().query(sql,
                                     new Object[] { ObjectType.VIEW.name(), ObjectType.PACKAGE_HEADER.name(),
                                                   ObjectType.PACKAGE.name(), ObjectType.TRIGGER.name(),
                                                   ObjectType.PROCEDURE.name(), ObjectType.FUNCTION.name(), moduleName },
                                     new PropagationObjectRowMapper());
    } catch (final BadSqlGrammarException ex) {
      LOG.error(ex);
      LOG.info("Creating " + propagationObjectsTable + ", because it does not exist.");
      createPropagationObjectsTable();
      return new ArrayList<PropagationObject>();
    }
  }

  public List<PropagationObject> getPropagationObjects() {
    String sql = "SELECT * FROM " + propagationObjectsTable + " WHERE object_type IN (?,?,?,?,?,?)";
    if (LOG.isDebugEnabled()) {
      LOG.debug("Loading propagation objects: " + sql);
    }

    try {
      return getJdbcTemplate().query(sql,
                                     new Object[] { ObjectType.VIEW.name(), ObjectType.PACKAGE_HEADER.name(),
                                                   ObjectType.PACKAGE.name(), ObjectType.TRIGGER.name(),
                                                   ObjectType.PROCEDURE.name(), ObjectType.FUNCTION.name() },
                                     new PropagationObjectRowMapper());
    } catch (final BadSqlGrammarException ex) {
      LOG.error(ex);
      LOG.info("Creating " + propagationObjectsTable + ", because it does not exist.");
      createPropagationObjectsTable();
      return new ArrayList<PropagationObject>();
    }
  }

  private void createPropagationObjectsTable() {
    getJdbcTemplate().update(getCreatePropagationObjectsSQL());
  }

  public boolean hasPropagationObjectEntry(String objectName, String moduleName) {
    String sql = "SELECT 1 FROM " + propagationObjectsTable + " WHERE object_name = ? AND module_name = ?";
    try {
      return getJdbcTemplate().queryForInt(sql, new Object[] { objectName, moduleName }) == 1;
    } catch (EmptyResultDataAccessException e) {
      return false;
    }
  }

  public void insertPropagationObjectEntry(PropagationObject propagationObject) {
    String sql =
        "INSERT INTO " + propagationObjectsTable
            + " (MODULE_NAME, OBJECT_NAME, OBJECT_TYPE, MD5_HASH, VERSION) VALUES (?,?,?,?,?)";
    getJdbcTemplate().update(sql,
                             new Object[] { propagationObject.getModuleName(), propagationObject.getObjectName(),
                                           propagationObject.getObjectType().name(), propagationObject.getMd5Hash(),
                                           propagationObject.getVersion() == null ? "" : propagationObject.getVersion() });
  }

  public void removePropagationObjectEntry(String objectName, String moduleName) {
    String removeSql = "DELETE FROM " + propagationObjectsTable + " WHERE object_name = ? AND module_name = ?";
    getJdbcTemplate().update(removeSql, new Object[] { objectName, moduleName });
  }

  public void updatePropagationObjectEntry(PropagationObject propagationObject) {
    String update =
        "UPDATE " + propagationObjectsTable
            + " SET md5_hash = ?, version = ? WHERE object_name = ? AND module_name = ?";
    getJdbcTemplate().update(update,
                             new Object[] { propagationObject.getMd5Hash(), propagationObject.getVersion(),
                                           propagationObject.getObjectName(), propagationObject.getModuleName() });
  }

  public void createLockTable() {
    getJdbcTemplate().update(getCreateLockSQL());
    getJdbcTemplate().update("INSERT INTO " + lockTable + " VALUES (NULL, ?)", new Object[] { new Date() });
  }

  public void insertLockRecord() {
    getJdbcTemplate().update("INSERT INTO " + lockTable + " VALUES (NULL, ?)", new Object[] { new Date() });
  }

  public void deleteLock() {
    getJdbcTemplate().update("UPDATE " + lockTable + " SET TOKEN = NULL");
  }

  public String getLockToken() {
    return (String) getJdbcTemplate().queryForObject("SELECT token FROM " + lockTable, String.class);
  }

  public int updateLock(String token) {
    return getJdbcTemplate().update("UPDATE " + lockTable + " SET TOKEN = ?, LOCK_TIME = ? WHERE TOKEN IS NULL",
                                    new Object[] { token, new Date() });
  }

  public void checkHistoryTable() {
    getJdbcTemplate().execute("SELECT 1 FROM " + historyLogTable);
  }

  public void createHistoryLogTable() {
    getJdbcTemplate().update(getCreateHistoryLogSQL());
  }

  public void insertHistoryLog(String moduleName, HistoryLogEntry entry) {
    String sqlText = StringUtils.abbreviate(entry.sqlText, MAX_SQLTEXT_LENGTH);
    String errorText = StringUtils.abbreviate(entry.sqlErrorText, MAX_ERRORTEXT_LENGTH);
    getJdbcTemplate().update("INSERT INTO "
                                 + historyLogTable
                                 + "(MODULE_NAME, DB_CHANGE_CODE, SQL_TEXT, ROWS_UPDATED, ERROR_CODE, ERROR_TEXT, UPDATE_TIME, EXECUTION_TIME) VALUES(?,?,?,?,?,?,?,?) ",
                             new Object[] { moduleName, entry.dbChange == null ? "" : entry.dbChange,
                                           sqlText == null ? "" : sqlText, entry.sqlRows, entry.sqlErrorCode,
                                           errorText == null ? "" : errorText, entry.date, entry.executionTime });
  }

  public void updateVersion() {
    try {
      getJdbcTemplate().queryForList("SELECT 1 FROM " + propagationObjectsTable + " WHERE version = ?",
                                     new Object[] { "MARKER" });
    } catch (final BadSqlGrammarException ex) {
      addVersionColumn();
    }
  }

  private void addVersionColumn() {
    String sql = "ALTER TABLE " + propagationObjectsTable + " ADD version " + getVarcharDefinition(30);

    try {
      getJdbcTemplate().update(sql);
    } catch (BadSqlGrammarException e) {
      LOG.error(e);
      LOG.info("Creating " + propagationObjectsTable + ", because it does not exist.");
      createPropagationObjectsTable();
    }
  }

  private String getCreateHistorySQL() {
    if (createHistorySQL == null) {
      String createHistoryFilePath = databaseCode + "_create_history.sql";
      return readSqlFromFile(createHistoryFilePath).replaceAll(TABLE_NAME_MARKER, changeHistoryTable);
    }
    return createHistorySQL;
  }

  private String getCreatePropagationObjectsSQL() {
    if (createPropagationObjectsSQL == null) {
      String createPropagationObjectsFilePath = databaseCode + "_create_propagation_objects.sql";
      return readSqlFromFile(createPropagationObjectsFilePath).replaceAll(TABLE_NAME_MARKER, propagationObjectsTable);
    }
    return createPropagationObjectsSQL;
  }

  private String getCreateLockSQL() {
    if (createLockSQL == null) {
      String createLockFilePath = databaseCode + "_create_lock.sql";
      return readSqlFromFile(createLockFilePath).replaceAll(TABLE_NAME_MARKER, lockTable);
    }
    return createLockSQL;
  }

  private String getCreateHistoryLogSQL() {
    if (createHistoryLogSQL == null) {
      String createHistoryLogFilePath = databaseCode + "_create_history_log.sql";
      return readSqlFromFile(createHistoryLogFilePath).replaceAll(TABLE_NAME_MARKER, historyLogTable);
    }
    return createHistoryLogSQL;
  }

  private String readSqlFromFile(String filePath) {
    // Enumeration<URL> findEntries = bundleContext.getBundle().findEntries(".", filePath, false);

    ClassPathResource classPathResource = new ClassPathResource("META-INF/databata/" + filePath);
    try {
      return PropagationUtils.readFile(classPathResource.getInputStream());
    } catch (IOException e) {
      LOG.error("Cannot read " + filePath + " file from classpath.");
      throw new RuntimeException(e);
    }
  }

  private String getVarcharDefinition(int charNumber) {
    if ("ORA".equals(databaseCode)) {
      return "VARCHAR2(" + charNumber + " char)";
    }

    return "VARCHAR " + charNumber;
  }

  public void setChangeHistoryTable(String changeHistoryTable) {
    this.changeHistoryTable = changeHistoryTable;
  }

  public void setPropagationObjectsTable(String propagationObjectsTable) {
    this.propagationObjectsTable = propagationObjectsTable;
  }

  public void setLockTable(String lockTable) {
    this.lockTable = lockTable;
  }

  public void setHistoryLogTable(String historyLogTable) {
    this.historyLogTable = historyLogTable;
  }

  public void setDatabaseCode(String databaseCode) {
    this.databaseCode = databaseCode;
  }
}