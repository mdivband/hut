package server.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import server.QueueManager;
import server.Simulator;
import server.QueueManager.MessagePublisher;
import server.model.State;
import java.beans.Transient;
import java.lang.annotation.Target;

import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class MessagePublisherTest {
    
    Simulator sim = new Simulator();
    QueueManager qm = new QueueManager(sim);

    @Test
    @DisplayName("Test for MessagePublisher Class")
    void InitTest(){
        QueueManager.MessagePublisher mp = qm.new MessagePublisher();
        assertNotNull(mp);
    }

    @Test
    @DisplayName("Test for publishMessage Function")
    void publishMessageTest(){
        QueueManager.MessagePublisher mp = qm.new MessagePublisher();
        boolean pm = mp.publishMessage("Queue Name Test", "Test Message for publishMessage");
        assertEquals(true,pm);
    }
}
