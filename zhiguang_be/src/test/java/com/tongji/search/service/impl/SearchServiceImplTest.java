package com.tongji.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.tongji.counter.service.CounterService;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchServiceImplTest {

    @Test
    void searchVisibilityRequiresPublishedPublicPost() {
        KnowPostMapper mapper = mock(KnowPostMapper.class);
        when(mapper.findDetailById(1L)).thenReturn(row("published", "public"));
        when(mapper.findDetailById(2L)).thenReturn(row("published", "private"));
        when(mapper.findDetailById(3L)).thenReturn(row("draft", "public"));

        SearchServiceImpl service = new SearchServiceImpl(
                mock(ElasticsearchClient.class),
                mock(CounterService.class),
                mapper
        );

        assertThat(service.isSearchVisible("1")).isTrue();
        assertThat(service.isSearchVisible("2")).isFalse();
        assertThat(service.isSearchVisible("3")).isFalse();
        assertThat(service.isSearchVisible("not-a-number")).isFalse();
    }

    private KnowPostDetailRow row(String status, String visible) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setStatus(status);
        row.setVisible(visible);
        return row;
    }
}
