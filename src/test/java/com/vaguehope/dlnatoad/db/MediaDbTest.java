package com.vaguehope.dlnatoad.db;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Random;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MediaDbTest {

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	private Random rnd;
	private File dbFile;
	private MediaDb undertest;

	@Before
	public void before () throws Exception {
		this.rnd = new Random();
		this.dbFile = this.tmp.newFile("id-db.db3");
		this.undertest = new MediaDb(this.dbFile);
	}

	@Test
	public void itConnectsToExistingDb () throws Exception {
		new MediaDb(this.dbFile);
	}

	@Test
	public void itReturnsSameIdForSameFile () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		assertEquals(
				this.undertest.idForFile(f1),
				this.undertest.idForFile(f1));
	}

	@Test
	public void itReturnsDifferentIdsForDifferentFile () throws Exception {
		assertThat(this.undertest.idForFile(mockMediaFile("media-1.ext")),
				not(equalTo(this.undertest.idForFile(mockMediaFile("media-2.ext")))));
	}

	@Test
	public void itReturnsSameIdForIdenticalFiles () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final File f2 = this.tmp.newFile("media-2.ext");
		FileUtils.copyFile(f1, f2, false);
		assertEquals(
				this.undertest.idForFile(f1),
				this.undertest.idForFile(f2));
	}

	@Test
	public void itReturnsSameIdWhenFileContentChanges () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final String id1 = this.undertest.idForFile(f1);
		fillFile(f1);
		assertEquals(id1, this.undertest.idForFile(f1));
	}

	@Test
	public void itChangesToSameIdWhenFileBecomesSameAsAnother () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final File f2 = mockMediaFile("media-2.ext");
		final String id1 = this.undertest.idForFile(f1);
		assertThat(id1, not(equalTo(this.undertest.idForFile(f2))));

		FileUtils.copyFile(f1, f2, false);
		assertEquals(id1, this.undertest.idForFile(f2));
	}

	@Test
	public void itGivesNewIdWhenFilesDiverge () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final String id1 = this.undertest.idForFile(f1);

		final File f2 = this.tmp.newFile("media-2.ext");
		FileUtils.copyFile(f1, f2, false);
		assertEquals(id1, this.undertest.idForFile(f2));

		fillFile(f2);
		assertThat(id1, not(equalTo(this.undertest.idForFile(f2))));
	}

	@Test
	public void itHandlesFileConvergingAndThenDiverging () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final File f2 = mockMediaFile("media-2.ext");
		final String id1 = this.undertest.idForFile(f1);
		assertThat(id1, not(equalTo(this.undertest.idForFile(f2))));

		FileUtils.copyFile(f1, f2, false);
		assertEquals(id1, this.undertest.idForFile(f2));

		fillFile(f2);
		assertThat(id1, not(equalTo(this.undertest.idForFile(f2))));
	}

	@Test
	public void itRevertsIdWhenFileContentReverts () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final File f2 = mockMediaFile("media-2.ext");
		final String id1 = this.undertest.idForFile(f1);
		final String id2 = this.undertest.idForFile(f2);
		assertThat(id1, not(equalTo(id2)));

		final File backup = this.tmp.newFile("backup");
		FileUtils.copyFile(f2, backup, false);

		FileUtils.copyFile(f1, f2, false);
		assertEquals(id1, this.undertest.idForFile(f2));

		FileUtils.copyFile(backup, f2, false);
		assertEquals(id2, this.undertest.idForFile(f2));
	}

	@Test
	public void itKeepsIdThroughMoveAndChange () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final String id1 = this.undertest.idForFile(f1);

		final File f2 = this.tmp.newFile("media-01.ext");
		f2.delete();
		FileUtils.moveFile(f1, f2);
		assertEquals(id1, this.undertest.idForFile(f2));

		fillFile(f2);
		assertEquals(id1, this.undertest.idForFile(f2));
	}

	@Test
	public void itKeepsIdThroughCopyDeleteAndChange () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final String id1 = this.undertest.idForFile(f1);

		final File f2 = this.tmp.newFile("media-01.ext");
		f2.delete();
		FileUtils.copyFile(f1, f2, false);
		assertEquals(id1, this.undertest.idForFile(f2));

		f1.delete();
		assertEquals(id1, this.undertest.idForFile(f2));

		fillFile(f2);
		assertEquals(id1, this.undertest.idForFile(f2));
	}

	@Test
	public void itKeepsIdThroughCopyDeleteAndChangeMultiple () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		final String id1 = this.undertest.idForFile(f1);

		final File f2 = this.tmp.newFile("media-01.ext");
		f2.delete();
		FileUtils.copyFile(f1, f2, false);
		assertEquals(id1, this.undertest.idForFile(f2));

		final File f3 = this.tmp.newFile("media-001.ext");
		f3.delete();
		FileUtils.copyFile(f1, f3, false);
		assertEquals(id1, this.undertest.idForFile(f3));

		f1.delete();
		assertEquals(id1, this.undertest.idForFile(f2));

		f3.delete();
		assertEquals(id1, this.undertest.idForFile(f2));

		fillFile(f2);
		assertEquals(id1, this.undertest.idForFile(f2));
	}

	@Test
	public void itStoresAndRetrivesDuration () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		this.undertest.storeFileDurationMillis(f1, 1234567890123L);
		assertEquals(1234567890123L, this.undertest.readFileDurationMillis(f1));
	}

	@Test
	public void itReturnsZeroWhenFileSizeChangesUpdates () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		this.undertest.storeFileDurationMillis(f1, 1234567890123L);
		FileUtils.writeStringToFile(f1, "abc", Charset.forName("UTF-8"));
		assertEquals(0L, this.undertest.readFileDurationMillis(f1));
	}

	@Test
	public void itUpdatesStoredDuration () throws Exception {
		final File f1 = mockMediaFile("media-1.ext");
		this.undertest.storeFileDurationMillis(f1, 1234567890123L);
		this.undertest.storeFileDurationMillis(f1, 12345678901234L);
	}

	private File mockMediaFile (final String name) throws IOException {
		final File f = this.tmp.newFile(name);
		fillFile(f);
		return f;
	}

	private void fillFile (final File f) throws IOException {
		final int l = (1024 * 10) + this.rnd.nextInt(1024 * 10);
		final byte[] b = new byte[l];
		this.rnd.nextBytes(b);
		FileUtils.writeByteArrayToFile(f, b);
	}

}
