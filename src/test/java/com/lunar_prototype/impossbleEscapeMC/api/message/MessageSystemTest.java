package com.lunar_prototype.impossbleEscapeMC.api.message;

import org.junit.jupiter.api.Test;

/** main形式の検証をMaven/Actionsでも必須にする。 */
public final class MessageSystemTest {
    @Test void catalog() throws Exception {
        MessageCatalogTest.main(new String[0]);
    }
    @Test void dispatch() throws Exception {
        MessageServiceTest.main(new String[0]);
    }
    @Test void architecture() throws Exception {
        MessageRoutingArchitectureTest.main(new String[0]);
    }
}
