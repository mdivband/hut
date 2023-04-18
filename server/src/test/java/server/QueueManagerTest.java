package server.model;

import static org.junit.jupiter.api.Assertions.*;

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
import org.junit.jupiter.params.provider.ValueSource;
import java.lang.reflect.*;
import java.nio.channels.Channel;


public class QueueManagerTest {

    Simulator simulator = new Simulator();
    QueueManager qManager = new QueueManager(simulator);
    Method gdm[] = QueueManager.class.getDeclaredMethods();

    @Test
    @DisplayName("Test for QueueManager Initialization")
    void initTest(){
        qManager = new QueueManager(simulator);
        assertEquals(qManager.getClass(), QueueManager.class);
    }

    @Test
    @DisplayName("Test for getCloudURI Function")
    void getCloudURITest(){
        assertEquals(qManager.getCloudURI(),"amqp://oijjhrkf:NtrL2nSC2I0Darx3q2S_SA7D0Eig-loz@lion.rmq.cloudamqp.com/oijjhrkf");
    }

    @Test
    @DisplayName("Test for getNewChannel Function")
    void getNewChannelTest(){
        try{
            Method m = gdm[1];
            Object chn = m.invoke(qManager);
            assertNotNull(chn);
        }
        catch(Exception e)
        {
            System.out.println("Excpetion: "+e);
        }
    }

    @Test
    @DisplayName("Test for initConnectionFactory Function")
    void initConnectionFactoryTest(){
        try{
            Method icft = gdm[2];
            Object icftm = icft.invoke(qManager);
            assertEquals(true,icftm);
        }
        catch(Exception e)
        {
            System.out.println("Exception: "+e);
        }
    }

    @Test
    @DisplayName("Test for createMessagepusblisher Function")
    void createMessagePublisherTest(){
        Method qManagerMethods[] = QueueManager.class.getDeclaredMethods();
        Method createMessagePublisher = qManagerMethods[4];
        try{
            createMessagePublisher.setAccessible(true);
            Object retObj = createMessagePublisher.invoke(qManager);
            assertNotNull(retObj);
        }
        catch(Exception e)
        {
            System.out.println("Exception: "+e);
        }
        MessagePublisher msg_pub  = qManager.createMessagePublisher() ;
        assertEquals(msg_pub.getClass(),MessagePublisher.class);
    }
}
