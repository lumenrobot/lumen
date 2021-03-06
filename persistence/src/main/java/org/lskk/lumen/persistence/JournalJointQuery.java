package org.lskk.lumen.persistence;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.data.domain.Pageable;

/**
 * Executes a Neo4j Cypher query.
 * Created by Budhi on 22/01/2015.
 *
 * @todo should be {@link Pageable}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type", defaultImpl = JournalJointQuery.class)
@JsonSubTypes(@JsonSubTypes.Type(name = "JournalJointQuery", value = JournalJointQuery.class))
public class JournalJointQuery {
    public String getMaxDateCreated() {
        return maxDateCreated;
    }

    public void setMaxDateCreated(String maxDateCreated) {
        this.maxDateCreated = maxDateCreated;
    }

    public Integer getItemsPerPage() {
        return itemsPerPage;
    }

    public void setItemsPerPage(Integer itemsPerPage) {
        this.itemsPerPage = itemsPerPage;
    }

    private String maxDateCreated;
    /**
     * From hydra:itemsPerPage
     */
    private Integer itemsPerPage;
}
