package edu.stanford.nlp.sempre.thingtalk;

import java.io.Closeable;
import java.io.IOException;
import java.sql.*;
import java.util.Iterator;

import org.apache.commons.dbcp2.BasicDataSource;

import edu.stanford.nlp.sempre.*;
import fig.basic.Option;

public class ThingpediaLexicon {
	public static class Options {
		@Option
		public String dbUrl = "jdbc:mysql://localhost/thingengine";
		@Option
		public String dbUser = "thingengine";
		@Option
		public String dbPw = "thingengine";
	}

	public static Options opts = new Options();

	public static abstract class Entry {
		public abstract Formula toFormula();

		public abstract String getRawPhrase();

		public void addFeatures(FeatureVector vec) {
		}
	}

	private static class AppEntry extends Entry {
		public final String rawPhrase;
		public final long userId;
		public final String appId;

		public AppEntry(String rawPhrase, long userId, String appId) {
			this.rawPhrase = rawPhrase;
			this.userId = userId;
			this.appId = appId;
		}

		@Override
		public String getRawPhrase() {
			return rawPhrase;
		}

		@Override
		public Formula toFormula() {
			return new ValueFormula<>(new NameValue("tt:app." + userId + "." + appId));
		}

		@Override
		public String toString() {
			return "[ " + rawPhrase + " => " + toFormula() + " ]";
		}
	}

	private static class ChannelEntry extends Entry {
		public final String rawPhrase;
		public final String kind;
		public final String name;

		public ChannelEntry(String rawPhrase, String kind, String name) {
			this.rawPhrase = rawPhrase;
			this.kind = kind;
			this.name = name;
		}

		@Override
		public String getRawPhrase() {
			return rawPhrase + " on " + kind;
		}

		@Override
		public Formula toFormula() {
			return new ValueFormula<>(new NameValue("tt:" + kind + "." + name));
		}

		@Override
		public String toString() {
			return "[ " + getRawPhrase() + " => " + toFormula() + " ]";
		}

		@Override
		public void addFeatures(FeatureVector vec) {
			vec.add("kinds", kind);
		}
	}

	private static ThingpediaLexicon instance;

	private final BasicDataSource dataSource;

	private ThingpediaLexicon() {
		dataSource = new BasicDataSource();
		dataSource.setDriverClassName("com.mysql.jdbc.Driver");
		dataSource.setUrl(opts.dbUrl);
		dataSource.setUsername(opts.dbUser);
		dataSource.setPassword(opts.dbPw);
	}

	public static ThingpediaLexicon getSingleton() {
		if (instance == null)
			instance = new ThingpediaLexicon();

		return instance;
	}

	static {
		try {
			Class.forName("com.mysql.jdbc.Driver");
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public static abstract class EntryStream implements Iterator<Entry>, Closeable, AutoCloseable
	{
		protected final ResultSet rs;
		private final Statement stmt;
		private final Connection con;
		private Entry nextEntry;

		public EntryStream(Connection con, Statement stmt, ResultSet rs) {
			this.con = con;
			this.stmt = stmt;
			this.rs = rs;
			nextEntry = null;
		}

		@Override
		public void close() throws IOException {
			try {
				con.close();
				stmt.close();
				rs.close();
			} catch (SQLException e) {
				throw new IOException(e);
			}
		}

		protected abstract Entry createEntry() throws SQLException;

		private void checkNext() {
			try {
			if (nextEntry != null)
				return;
			if (!rs.next())
				return;
			nextEntry = createEntry();
			} catch(SQLException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public boolean hasNext() {
			checkNext();
			return nextEntry != null;
		}

		@Override
		public Entry next() {
			checkNext();
			Entry next = nextEntry;
			nextEntry = null;
			return next;
		}
	}

	private static class AppEntryStream extends EntryStream {
		public AppEntryStream(Connection con, Statement stmt, ResultSet rs) {
			super(con, stmt, rs);
		}

		@Override
		protected AppEntry createEntry() throws SQLException {
			return new AppEntry(rs.getString(1), rs.getLong(2), rs.getString(3));
		}
	}

	private static class ChannelEntryStream extends EntryStream {
		public ChannelEntryStream(Connection con, Statement stmt, ResultSet rs) {
			super(con, stmt, rs);
		}

		@Override
		protected ChannelEntry createEntry() throws SQLException {
			return new ChannelEntry(rs.getString(1), rs.getString(2), rs.getString(3));
		}
	}

	public EntryStream lookupApp(String phrase) throws SQLException {
		String query = "";
		if(Builder.opts.parser.equals("BeamParser")) {
			query = "select canonical,owner,appId from app where canonical = ?";
		} else {
			query = "select canonical,owner,appId from app where match canonical against (? in natural language mode)";
		}

		Connection con = dataSource.getConnection();
		PreparedStatement stmt = con.prepareStatement(query);
		stmt.setString(1, phrase);
		
		return new AppEntryStream(con, stmt, stmt.executeQuery());
	}

	public EntryStream lookupChannel(String phrase) throws SQLException {
		String query = "";
		if(Builder.opts.parser.equals("BeamParser")) {
			query ="select dsc.canonical,ds.kind,dsc.name from device_schema_channels dsc, device_schema ds "
					+ " where dsc.schema_id = ds.id and dsc.version = ds.approved_version and canonical = ?";
		} else {
			query ="select dsc.canonical,ds.kind,dsc.name from device_schema_channels dsc, device_schema ds "
					+ " where dsc.schema_id = ds.id and dsc.version = ds.approved_version and match canonical against (? in natural language mode)";
		}

		Connection con = dataSource.getConnection();
		PreparedStatement stmt = con.prepareStatement(query);
		stmt.setString(1, phrase);

		return new ChannelEntryStream(con, stmt, stmt.executeQuery());
	}
}