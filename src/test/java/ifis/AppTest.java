package ifis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.Test;

/**
 * Unit test for simple App.
 */
class AppTest {

    private static final String INPUT_DIR = "shapes/test/input/";
    private static final String EXPECTED_DIR = "shapes/test/expected/";
    private static final String ENDPOINT = "http://localhost:8080/sparql/";

    /**
     * Rigorous Test.
     */
    @Test
    void testApp() {
        assertEquals(1, 1);
    }
    @Test
    void testConstraints() {
        assertTrue(true);
        
    }

    @Test
    void updateExpectedResults() throws Exception {
         var inputDir = Paths.get(INPUT_DIR);

        Files.walk(inputDir)
            .skip(1)
            .forEach((inputFile)->{
                // Run App
                try {
                    System.err.println("--------------- TESTING " + inputFile.getFileName() + " --------------- ");
                    App.main(new String[]{inputFile.toAbsolutePath().toString(), ENDPOINT});
                } catch (Exception e) {
                    System.err.println("--------------- FAILURE AT TEST " + inputFile.getFileName() + " --------------- ");
                    e.printStackTrace();
                    fail();
                }
                // Read report
                try {
                    Files.copy(Path.of("report.log"), Path.of(EXPECTED_DIR + inputFile.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    e.printStackTrace();
                    fail();
                }

            })
            ;
    }


    @Test
    void testBatchProcessing() throws IOException, Exception {


        var inputDir = Paths.get(INPUT_DIR);

        Files.walk(inputDir)
            .skip(1)
            .forEach((inputFile)->{
                // Run App
                try {
                    System.err.println("--------------- TESTING " + inputFile.getFileName() + " --------------- ");
                    App.main(new String[]{inputFile.toAbsolutePath().toString(), ENDPOINT});
                } catch (Exception e) {
                    System.err.println("--------------- FAILURE AT TEST " + inputFile.getFileName() + " --------------- ");
                    e.printStackTrace();
                    fail();
                }
                // Read report
                var pass = FileComparer.compareFiles(new File("report.log"), new File("shapes/test/expected/"+inputFile.getFileName()));

                if (!pass) fail("FAILURE AT TEST " + inputFile.getFileName());

            })
            ;

    }

    static class FileComparer {

        public static boolean compareFiles(File file1, File file2) {
            try {

                /* 
                 * First check sizes
                 */
                
                if (file1.length() != file2.length()) return false; 
                
                
                

                var fis1 = new BufferedInputStream(new FileInputStream(file1));
                var fis2 = new BufferedInputStream(new FileInputStream(file1));

                int byte1, byte2;

                while ((byte1 = fis1.read()) != -1 && (byte2 = fis2.read()) != -1) {
                    if (byte1 != byte2) {
                        fis1.close();
                        fis2.close();
                        return false;
                    }
                }

                fis1.close();
                fis2.close();

                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
    }
}


