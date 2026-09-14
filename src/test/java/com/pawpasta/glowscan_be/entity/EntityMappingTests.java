package com.pawpasta.glowscan_be.entity;

import com.pawpasta.glowscan_be.modal.entity.*;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypesScanner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityMappingTests {
    @Test
    void allMappingsCanBuildWithoutDatabaseAccess() {
        assertMappingsBuild(List.of(User.class, Role.class, Permission.class,
                UserRole.class, RolePermission.class, UserDevice.class, RefreshToken.class,
                PushRegistration.class, ActionToken.class, AuthAuditLog.class));
    }

    @Test
    void allMappingsCanBuildInSpringScanOrderWithoutDatabaseAccess() throws ClassNotFoundException {
        var managedTypes = new PersistenceManagedTypesScanner(new DefaultResourceLoader())
                .scan(User.class.getPackageName());
        var entities = new ArrayList<Class<?>>();
        for (String className : managedTypes.getManagedClassNames()) {
            entities.add(Class.forName(className));
        }
        assertMappingsBuild(entities);
    }

    private void assertMappingsBuild(List<Class<?>> entities) {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        try {
            var sources = new MetadataSources(registry);
            for (Class<?> entity : entities) {
                sources.addAnnotatedClass(entity);
            }
            var metadata = sources.buildMetadata();
            assertEquals(10, metadata.getEntityBindings().size());
            try (var factory = metadata.buildSessionFactory()) {
                assertEquals(10, factory.getMetamodel().getEntities().size());
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
