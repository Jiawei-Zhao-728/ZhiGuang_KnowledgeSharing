package com.tongji.search.index;

import com.tongji.knowpost.model.KnowPostDetailRow;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchIndexServiceTest {

    private final SearchIndexService service = new SearchIndexService(null, null, null, null);

    @Test
    void onlyPublishedPublicPostsAreSearchable() {
        KnowPostDetailRow publicPost = row("published", "public");
        KnowPostDetailRow privatePost = row("published", "private");
        KnowPostDetailRow draftPost = row("draft", "public");

        assertThat(service.isPubliclySearchable(publicPost)).isTrue();
        assertThat(service.isPubliclySearchable(privatePost)).isFalse();
        assertThat(service.isPubliclySearchable(draftPost)).isFalse();
        assertThat(service.isPubliclySearchable(null)).isFalse();
    }

    private KnowPostDetailRow row(String status, String visible) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setStatus(status);
        row.setVisible(visible);
        return row;
    }
}
