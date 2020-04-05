package com.vaguehope.dlnatoad.dlnaserver;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.Item;
import org.junit.Before;
import org.junit.Test;

public class ContentTreeTest {

	private ContentTree undertest;
	private MockContent mockContent;

	@Before
	public void before() throws Exception {
		this.undertest = new ContentTree();
		this.mockContent = new MockContent(this.undertest);
	}

	@Test
	public void itHasItems() throws Exception {
		this.mockContent.givenMockItems(10);
		assertThat(MockContent.listOfItems(this.undertest.getNodes()), hasSize(10));
	}

	@Test
	public void itMakesRecentContainer() throws Exception {
		final List<ContentNode> mockItems = this.mockContent.givenMockItems(100, sequentialTimeStamps());
		final List<Item> expected = MockContent.listOfItems(mockItems).subList(mockItems.size() - 50, mockItems.size());
		Collections.reverse(expected);

		final ContentNode cn = undertest.getNode(ContentGroup.RECENT.getId());
		final Container c = cn.getContainer();
		assertThat(c.getItems(), equalTo(expected));
	}

	@Test
	public void itRemovesOldItemsFromRecent() throws Exception {
		final List<ContentNode> mockItems = this.mockContent.givenMockItems(100, sequentialTimeStamps());

		final Collection<ContentNode> recent = this.undertest.getRecent();
		assertThat(recent, hasSize(50));

		assertThat(recent.iterator().next().getId(), equalTo(mockItems.get(mockItems.size() - 1).getId()));
	}

	@Test
	public void itRemovesGoneItemsFromRecent() throws Exception {
		List<ContentNode> items = this.mockContent.givenMockItems(1);
		assertThat(this.undertest.getRecent(), hasSize(1));

		when(items.get(0).getFile().exists()).thenReturn(false);
		undertest.prune();
		assertThat(this.undertest.getRecent(), hasSize(0));
	}

	private Consumer<File> sequentialTimeStamps() {
		return new Consumer<File>() {
			long time = 0L;
			@Override
			public void accept(File f) {
				time += 1;
				when(f.lastModified()).thenReturn(time);
			}
		};
	}

}
