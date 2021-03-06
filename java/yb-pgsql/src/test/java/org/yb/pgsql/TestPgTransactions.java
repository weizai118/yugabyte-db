package org.yb.pgsql;

import org.apache.commons.lang3.RandomUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.postgresql.core.TransactionState;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yb.client.TestUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.yb.AssertionWrappers.assertEquals;
import static org.yb.AssertionWrappers.assertFalse;
import static org.yb.AssertionWrappers.assertTrue;

import org.yb.YBTestRunner;

import org.junit.runner.RunWith;

@RunWith(value=YBTestRunner.class)
public class TestPgTransactions extends BasePgSQLTest {
  private static final Logger LOG = LoggerFactory.getLogger(TestPgTransactions.class);

  protected void overridableCustomizePostgresEnvVars(Map<String, String> envVars) {
    // Temporary: YugaByte transactions are only enabled in the PostgreSQL API under a flag.
    envVars.put("YB_PG_TRANSACTIONS_ENABLED", "1");
  }

  @Test
  public void testBasicTransaction() throws Exception {
    Connection connection1 = createConnectionNoAutoCommit();
    createSimpleTable("test", "v");
    Statement statement = connection1.createStatement();

    // For the second connection we still enable auto-commit, so that every new SELECT will see
    // a new snapshot of the database.
    Connection connection2 = createConnectionWithAutoCommit();
    Statement statement2 = connection2.createStatement();

    for (int i = 0; i < 100; ++i) {
      try {
        int h1 = i * 10;
        int h2 = i * 10 + 1;
        LOG.debug("Inserting the first row within a transaction but not committing yet");
        statement.execute("INSERT INTO test(h, r, v) VALUES (" + h1 + ", 2, 3)");

        LOG.debug("Trying to read the first row from another connection");
        assertFalse(statement2.executeQuery("SELECT h, r, v FROM test WHERE h = " + h1).next());

        LOG.debug("Inserting the second row within a transaction but not committing yet");
        statement.execute("INSERT INTO test(h, r, v) VALUES (" + h2 + ", 5, 6)");

        LOG.debug("Trying to read the second row from another connection");
        assertFalse(statement2.executeQuery("SELECT h, r, v FROM test WHERE h = " + h2).next());

        LOG.debug("Committing the transaction");
        connection1.commit();

        LOG.debug("Checking first row from the other connection");
        ResultSet rs = statement2.executeQuery("SELECT h, r, v FROM test WHERE h = " + h1);
        assertTrue(rs.next());
        assertEquals(h1, rs.getInt("h"));
        assertEquals(2, rs.getInt("r"));
        assertEquals(3, rs.getInt("v"));

        LOG.debug("Checking second row from the other connection");
        rs = statement2.executeQuery("SELECT h, r, v FROM test WHERE h = " + h2);
        assertTrue(rs.next());
        assertEquals(h2, rs.getInt("h"));
        assertEquals(5, rs.getInt("r"));
        assertEquals(6, rs.getInt("v"));
      } catch (PSQLException ex) {
        LOG.error("Caught a PSQLException at iteration i=" + i, ex);
        throw ex;
      }
    }
  }

  /**
   * This test runs conflicting transactions trying to insert the same row and verifies that
   * exactly one of them gets committed.
   */
  @Test
  public void testTransactionConflicts() throws Exception {
    Connection connection1 = createConnectionNoAutoCommit();
    createSimpleTable("test", "v");
    Statement statement1 = connection1.createStatement();
    Connection connection2 = createConnectionNoAutoCommit();
    Statement statement2 = connection2.createStatement();
    int numFirstWinners = 0;
    int numSecondWinners = 0;
    final int totalIterations = TestUtils.nonTsanVsTsan(300, 100);
    for (int i = 1; i <= totalIterations; ++i) {
      if (RandomUtils.nextBoolean()) {
        // Shuffle the two connections between iterations.
        Connection tmpConnection = connection1;
        connection1 = connection2;
        connection2 = tmpConnection;

        Statement tmpStatement = statement1;
        statement1 = statement2;
        statement2 = tmpStatement;
      }

      executeWithTimeout(statement1,
          String.format("INSERT INTO test(h, r, v) VALUES (%d, %d, %d)", i, i, 100 * i));
      boolean executed2 = false;
      try {
        executeWithTimeout(statement2,
            String.format("INSERT INTO test(h, r, v) VALUES (%d, %d, %d)", i, i, 200 * i));
        executed2 = true;
      } catch (PSQLException ex) {
        // TODO: validate the exception message.
        // Not reporting a stack trace here on purpose, because this will happen a lot in a test.
        LOG.info("Error while inserting on the second connection:" + ex.getMessage());
      }
      TransactionState txnState1BeforeCommit = getPgTxnState(connection1);
      TransactionState txnState2BeforeCommit = getPgTxnState(connection2);

      boolean committed1 = commitAndCatchException(connection1, "first connection");
      TransactionState txnState1AfterCommit = getPgTxnState(connection1);

      boolean committed2 = commitAndCatchException(connection2, "second connection");
      TransactionState txnState2AfterCommit = getPgTxnState(connection2);

      LOG.info("i=" + i +
          " executed2=" + executed2 +
          " committed1=" + committed1 +
          " committed2=" + committed2 +
          " txnState1BeforeCommit=" + txnState1BeforeCommit +
          " txnState2BeforeCommit=" + txnState2BeforeCommit +
          " txnState1AfterCommit=" + txnState1AfterCommit +
          " txnState2AfterCommit=" + txnState2AfterCommit +
          " numFirstWinners=" + numFirstWinners +
          " numSecondWinners=" + numSecondWinners);
      if (executed2) {
        assertFalse(committed1);
        assertTrue(committed2);
        assertEquals(TransactionState.OPEN, txnState1BeforeCommit);
        assertEquals(TransactionState.OPEN, txnState2BeforeCommit);
        // It looks like when the commit of the first transaction throws an exception, the
        // transaction state changes to IDLE immediately.
        assertEquals(TransactionState.IDLE, txnState1AfterCommit);
        assertEquals(TransactionState.IDLE, txnState2AfterCommit);
        numSecondWinners++;
      } else {
        assertTrue(committed1);
        // It looks like in case we get an error on an operation on the second connection, the
        // commit on that connection succeeds. This makes sense in a way because the client already
        // knows that the second transaction failed from the original operation failure. BTW the
        // second transaction is already in a FAILED state before we successfully "commit" it:
        //
        // executed2=false
        // committed1=true
        // committed2=true
        // txnState1BeforeCommit=OPEN
        // txnState2BeforeCommit=FAILED
        // txnState1AfterCommit=IDLE
        // txnState2AfterCommit=IDLE
        //
        // TODO: verify if this is consistent with vanilla PostgreSQL behavior.
        // assertFalse(committed2);
        assertEquals(TransactionState.OPEN, txnState1BeforeCommit);
        assertEquals(TransactionState.FAILED, txnState2BeforeCommit);
        assertEquals(TransactionState.IDLE, txnState1AfterCommit);
        assertEquals(TransactionState.IDLE, txnState2AfterCommit);

        numFirstWinners++;
      }
    }
    LOG.info(String.format(
        "First txn won in %d cases, second won in %d cases", numFirstWinners, numSecondWinners));
    double skew = Math.abs(numFirstWinners - numSecondWinners) * 1.0 / totalIterations;
    LOG.info("Skew between the number of wins by two connections: " + skew);
    final double SKEW_THRESHOLD = 0.3;
    assertTrue("Expecting the skew to be below the threshold " + SKEW_THRESHOLD + ", got " + skew,
        skew < SKEW_THRESHOLD);
  }
}
