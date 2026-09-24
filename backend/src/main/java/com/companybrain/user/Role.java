package com.companybrain.user;

/**
 * What a signed-in user may do.
 * <ul>
 *   <li>{@code EMPLOYEE}: ask questions about documents visible to their department.</li>
 *   <li>{@code KNOWLEDGE_MANAGER}: also upload and delete documents and choose who can see them.</li>
 *   <li>{@code ADMIN}: everything, including users and departments; questions search every document.</li>
 * </ul>
 */
public enum Role {
    EMPLOYEE,
    KNOWLEDGE_MANAGER,
    ADMIN
}
