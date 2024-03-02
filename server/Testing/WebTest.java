import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import server.Simulator;
import server.model.State;
import server.model.task.Task;
import tool.GsonUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

public class WebTest {

    public static RemoteWebDriver driver = null;
    public static DesiredCapabilities capabilities = null;


    // TODO iterate over main browsers
    @Before
    public void initializeSelenium() throws MalformedURLException {
        try {
            LogManager.getLogManager().readConfiguration(new FileInputStream("./loggingForStudy.properties"));
        } catch (final IOException e) {
            Logger.getAnonymousLogger().severe("Could not load default loggingForStudy.properties file");
            Logger.getAnonymousLogger().severe(e.getMessage());
        }

        //Setup GSON
        GsonUtils.registerTypeAdapter(Task.class, Task.taskSerializer);
        GsonUtils.registerTypeAdapter(State.HazardHitCollection.class, State.hazardHitsSerializer);
        GsonUtils.create();
        new Simulator().start(44101);


        capabilities = new DesiredCapabilities();
        capabilities.setCapability("browserName", "firefox");
        //capabilities.setCapability("version", "70.0");
        //capabilities.setCapability("platform", "win11");

        driver = new RemoteWebDriver(new URL("http://localhost:44101/wd/hub"), capabilities);
    }

    /*
    @Test
    public void signUp() {
        driver.findElement(By.id("Name")).sendKeys("John Doe");
        driver.findElement(By.id("Email")).sendKeys("johndoe@testemail.com");
        driver.findElement(By.id("City")).sendKeys("Bangalore");
                driver.findElement(By.name("submit_btn")).click();

        String result = driver.findElementByXPath("/html/body/response").getText();

        assertEquals(result,"Sign-up Successful");
    }

     */

    @Test(timeout=5000)
    public void TestLaunchScenario() {
        driver.findElement(By.id("buttonLoadScenario")).click();
        driver.findElement(By.name("MainTest.json")).click();

        List<WebElement> list = driver.findElements(By.xpath("//*[contains(text(),'" + "Time:" + "')]"));
        Assert.assertTrue("MainTest seems to not have loaded properly", list.size() > 0);
    }

    /*
    @Test(timeout=5000)
    public void test_search()
    {
// code to search something using the search_bar
        driver.findElement(By.id("search_bar")).sendKeys("Browserstack automation testing");
        driver.findElement(By.name("search_btn")).click();

        String first_result = driver.findElementByXPath("/html/body/result").getText();

        assertEquals(first_result,"Testing and annotations");
    }

     */

    @AfterClass
    public static void cleanUp() {
        driver.quit(); // close the browser
    }
}