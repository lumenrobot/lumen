package id.ac.itb.lumen.persistence

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import groovy.transform.CompileStatic

/**
 * Executes a Neo4j Cypher query.
 * Created by Budhi on 22/01/2015.
 * @todo should be {@link org.springframework.data.domain.Pageable}
 */
@CompileStatic
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use=JsonTypeInfo.Id.NAME, property="@type", defaultImpl=JournalJointQuery.class)
@JsonSubTypes(@JsonSubTypes.Type(name="JournalJointQuery", value=JournalJointQuery.class))
class JournalJointQuery {
    String maxDateCreated
    /**
     * From hydra:itemsPerPage
     */
    Integer itemsPerPage
}