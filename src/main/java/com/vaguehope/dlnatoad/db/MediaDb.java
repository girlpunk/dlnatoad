package com.vaguehope.dlnatoad.db;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.Encoding;

public class MediaDb {

	private static final Logger LOG = LoggerFactory.getLogger(MediaDb.class);

	private final Connection dbConn;

	public MediaDb (final File dbFile) throws SQLException {
		this.dbConn = makeDbConnection(dbFile);
		makeSchema();
	}

	public String idForFile (final File file) throws SQLException, IOException {
		if (!file.isFile()) throw new IOException("Not a file: " + file.getAbsolutePath());

		FileData fileData = readFileData(file);
		if (fileData == null) {
			fileData = FileData.forFile(file);

			final Collection<FileAndData> otherFiles = missingFilesWithHash(fileData.getHash());
			final Set<String> otherIds = distinctIds(otherFiles);
			if (otherIds.size() == 1) {
				fileData = fileData.withId(otherIds.iterator().next());
			}
			else {
				fileData = fileData.withId(newUnusedId());
				otherFiles.clear(); // Did not merge, so do not remove.
			}

			storeFileData(file, fileData);
			removeFiles(otherFiles);
		}
		else if (!fileData.upToDate(file)) {
			final String prevHash = fileData.getHash();
			final String prevHashCanonicalId = canonicalIdForHash(prevHash);
			fileData = FileData.forFile(file).withId(fileData.getId());

			Collection<FileAndData> otherFiles = Collections.emptySet();
			if (prevHashCanonicalId != null && !prevHashCanonicalId.equals(fileData.getId())) {
				otherFiles = filesWithHash(prevHash);
				excludeFile(otherFiles, file); // Remove self.

				if (allMissing(otherFiles)) {
					fileData = fileData.withNewId(prevHashCanonicalId);
				}
				else {
					otherFiles.clear(); // Did not merge, so do not remove.
				}
			}

			updateFileData(file, fileData);
			removeFiles(otherFiles);
		}

		String id = canonicalIdForHash(fileData.getHash());
		if (id == null) {
			id = fileData.getId();
			storeCanonicalId(fileData.getHash(), id);
		}

		return id;
	}

	public long readFileDurationMillis (final File file) throws SQLException {
		return readDuration(file.getAbsolutePath(), file.length());
	}

	public void storeFileDurationMillis (final File file, final long duration) throws SQLException {
		storeDuration(file.getAbsolutePath(), file.length(), duration);
	}

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	private void makeSchema () throws SQLException {
		if (!tableExists("files")) {
			executeSql("CREATE TABLE files ("
					+ "file STRING NOT NULL PRIMARY KEY, size INT NOT NULL, modified INT NOT NULL, hash STRING NOT NULL, id STRING NOT NULL);");
		}
		if (!tableExists("hashes")) {
			executeSql("CREATE TABLE hashes ("
					+ "hash STRING NOT NULL PRIMARY KEY, id STRING NOT NULL);");
			executeSql("CREATE INDEX hashes_idx ON hashes (id);");
		}
		if (!tableExists("durations")) {
			executeSql("CREATE TABLE durations ("
					+ "key STRING NOT NULL PRIMARY KEY, size INT NOT NULL, duration INT NOT NULL);");
		}
	}

	private FileData readFileData (final File file) throws SQLException {
		final PreparedStatement st = this.dbConn.prepareStatement(
				"SELECT size, modified, hash, id FROM files WHERE file=?;");
		try {
			st.setString(1, file.getAbsolutePath());
			st.setMaxRows(2);
			final ResultSet rs = st.executeQuery();
			try {
				if (!rs.next()) return null;
				final FileData fileData = new FileData(
						rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4));
				if (rs.next()) throw new SQLException("Query for file '" + file.getAbsolutePath() + "' retured more than one result.");
				return fileData;
			}
			finally {
				rs.close();
			}
		}
		finally {
			st.close();
		}
	}

	private Collection<FileAndData> filesWithHash (final String hash) throws SQLException {
		final PreparedStatement st = this.dbConn.prepareStatement(
				"SELECT file, size, modified, hash, id FROM files WHERE hash=?;");
		try {
			st.setString(1, hash);
			final ResultSet rs = st.executeQuery();
			try {
				final Collection<FileAndData> ret = new ArrayList<>();
				while (rs.next()) {
					ret.add(new FileAndData(
							new File(rs.getString(1)),
							new FileData(rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getString(5))));
				}
				return ret;
			}
			finally {
				rs.close();
			}
		}
		finally {
			st.close();
		}
	}

	private Collection<FileAndData> missingFilesWithHash (final String hash) throws SQLException {
		final Collection<FileAndData> files = filesWithHash(hash);
		excludeFilesThatStillExist(files);
		return files;
	}

	private void storeFileData (final File file, final FileData fileData) throws SQLException {
		final PreparedStatement st = this.dbConn.prepareStatement(
				"INSERT INTO files (file,size,modified,hash,id) VALUES (?,?,?,?,?);");
		try {
			st.setString(1, file.getAbsolutePath());
			st.setLong(2, fileData.getSize());
			st.setLong(3, fileData.getModified());
			st.setString(4, fileData.getHash());
			st.setString(5, fileData.getId());
			final int n = st.executeUpdate();
			if (n < 1) throw new SQLException("No insert occured inserting file '" + file.getAbsolutePath() + "'.");
		}
		finally {
			st.close();
		}
	}

	private void updateFileData (final File file, final FileData fileData) throws SQLException {
		final PreparedStatement st = this.dbConn.prepareStatement(
				"UPDATE files SET size=?,modified=?,hash=?,id=? WHERE file=?;");
		try {
			st.setLong(1, fileData.getSize());
			st.setLong(2, fileData.getModified());
			st.setString(3, fileData.getHash());
			st.setString(4, fileData.getId());
			st.setString(5, file.getAbsolutePath());
			final int n = st.executeUpdate();
			if (n < 1) throw new SQLException("No update occured updating file '" + file.getAbsolutePath() + "'.");
		}
		finally {
			st.close();
		}
	}

	private void removeFile (final File file) throws SQLException {
		final PreparedStatement st = this.dbConn.prepareStatement(
				"DELETE FROM files WHERE file=?;");
		try {
			st.setString(1, file.getAbsolutePath());
			final int n = st.executeUpdate();
			if (n < 1) throw new SQLException("No update occured removing file '" + file.getAbsolutePath() + "'.");
		}
		finally {
			st.close();
		}
	}

	private void removeFiles (final Collection<FileAndData> files) throws SQLException {
		for (final FileAndData file : files) {
			removeFile(file.getFile());
		}
	}

	private String canonicalIdForHash (final String hash) throws SQLException {
		final PreparedStatement st = this.dbConn.prepareStatement(
				"SELECT id FROM hashes WHERE hash=?;");
		try {
			st.setString(1, hash);
			st.setMaxRows(2);
			final ResultSet rs = st.executeQuery();
			try {
				if (!rs.next()) return null;
				final String id = rs.getString(1);
				if (rs.next()) throw new SQLException("Query for hash '" + hash + "' retured more than one result.");
				return id;
			}
			finally {
				rs.close();
			}
		}
		finally {
			st.close();
		}
	}

	private void storeCanonicalId (final String hash, final String id) throws SQLException {
		final PreparedStatement st = this.dbConn.prepareStatement(
				"INSERT INTO hashes (hash,id) VALUES (?,?);");
		try {
			st.setString(1, hash);
			st.setString(2, id);
			final int n = st.executeUpdate();
			if (n < 1) throw new SQLException("No update occured inserting hash '" + hash + "'.");
		}
		finally {
			st.close();
		}
	}

	private Collection<String> hashesForId (final String id) throws SQLException {
		final PreparedStatement st = this.dbConn.prepareStatement(
				"SELECT hash FROM hashes WHERE id=?;");
		try {
			st.setString(1, id);
			final ResultSet rs = st.executeQuery();
			try {
				final Collection<String> ret = new ArrayList<>();
				while(rs.next()) {
					ret.add(rs.getString(1));
				}
				return ret;
			}
			finally {
				rs.close();
			}
		}
		finally {
			st.close();
		}
	}

	private String newUnusedId () throws SQLException {
		while (true) {
			final String id = UUID.randomUUID().toString();
			if (hashesForId(id).size() < 1) return id;
			LOG.warn("Discarding colliding random UUID: {}", id);
		}
	}

	private void excludeFilesThatStillExist (final Collection<FileAndData> files) {
		for (final Iterator<FileAndData> i = files.iterator(); i.hasNext();) {
			if (i.next().getFile().exists()) i.remove();
		}
	}

	private void excludeFile (final Collection<FileAndData> files, final File file) {
		final int startSize = files.size();
		for (final Iterator<FileAndData> i = files.iterator(); i.hasNext();) {
			if (file.equals(i.next().getFile())) i.remove();
		}
		if (files.size() != startSize - 1) throw new IllegalStateException("Expected to only remove one item from list.");
	}

	private Set<String> distinctIds (final Collection<FileAndData> files) {
		final Set<String> ids = new HashSet<>();
		for (final FileAndData f : files) {
			ids.add(f.getData().getId());
		}
		return ids;
	}

	private boolean allMissing (final Collection<FileAndData> files) {
		for (final FileAndData file : files) {
			if (file.getFile().exists()) return false;
		}
		return true;
	}


//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	private void storeDuration (final String key, final long size, final long duration) throws SQLException {
		final PreparedStatement st = this.dbConn.prepareStatement(
				"INSERT INTO durations (key,size,duration) VALUES (?,?,?);");
		try {
			st.setString(1, key);
			st.setLong(2, size);
			st.setLong(3, duration);
			final int n = st.executeUpdate();
			if (n < 1) throw new SQLException("No insert occured inserting key '" + key + "'.");
		}
		finally {
			st.close();
		}
	}

	private long readDuration (final String key, final long expectedSize) throws SQLException {
		final PreparedStatement st = this.dbConn.prepareStatement(
				"SELECT size, duration FROM durations WHERE key=?;");
		try {
			st.setString(1, key);
			st.setMaxRows(2);
			final ResultSet rs = st.executeQuery();
			try {
				if (!rs.next()) return 0;

				final long storedSize = rs.getLong(1);
				final long duration = rs.getLong(2);

				if (rs.next()) throw new SQLException("Query for key '" + key + "' retured more than one result.");

				if (expectedSize != storedSize) return 0;
				return duration;
			}
			finally {
				rs.close();
			}
		}
		finally {
			st.close();
		}
	}

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	private static Connection makeDbConnection (final File dbFile) throws SQLException {
		final SQLiteConfig config = new SQLiteConfig();
		config.setEncoding(Encoding.UTF8);
//		config.setUserVersion(version); // TODO
		return DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath(), config.toProperties());
	}

	private boolean tableExists (final String tableName) throws SQLException {
		final Statement st = this.dbConn.createStatement();
		try {
			final ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + tableName + "';");
			try {
				return rs.next();
			}
			finally {
				rs.close();
			}
		}
		finally {
			st.close();
		}
	}

	private boolean executeSql (final String sql) throws SQLException {
		final Statement st = this.dbConn.createStatement();
		try {
			return st.executeUpdate(sql) > 0;
		}
		finally {
			st.close();
		}
	}

}
