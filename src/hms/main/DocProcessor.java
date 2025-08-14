package hms.main;

import jcifs.smb.*;
import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.util.Vector;

public class DocProcessor {

    private String mainDir = new FileManager().ReadFile()[1];
    Vector<String> pathV = new Vector<String>();
    Vector<String> dirV = new Vector<String>();
    private String tempFolderPath = "localTemp";
    static String currentDir = System.getProperty("user.dir");

    static final String SMB_USER = null;  // set your SMB user
    static final String SMB_PASS = null;  // set your SMB password
    static final String DOMAIN = null;    // set your domain or null

    private Vector<FileToProcess> filesToConvert = new Vector<FileToProcess>();

    public static void main(String[] args) {
        while (true) {
            try {
                new DocProcessor().startProcessing();
            } catch (Exception e) {
                e.printStackTrace();
                new DocProcessor().logErrorToDb("Fatal error in main loop", e);
            }

            try {
                Thread.sleep(60000); // wait 60 seconds
            } catch (InterruptedException e) {
                e.printStackTrace();
                new DocProcessor().logErrorToDb("Interrupted during sleep", e);
            }
        }
    }

    public void startProcessing() {
        try {
            new FileManager().deleteLocalTemp(new File(tempFolderPath));
            getAllDocPath();
            new FileManager().createDir(tempFolderPath);
            createAllTempDir();

            final NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(DOMAIN, SMB_USER, SMB_PASS);

            Vector<Thread> downloadThreads = new Vector<Thread>();
            for (int i = 0; i < pathV.size(); i++) {
                final String smbPath = mainDir + pathV.get(i) + "/";
                final String localPath = tempFolderPath + "/" + dirV.get(i);

                Thread t = new Thread(new Runnable() {
                    public void run() {
                        downloadDocsFromSmb(smbPath, localPath, auth);
                    }
                });

                downloadThreads.add(t);
                t.start();
            }

            for (int i = 0; i < downloadThreads.size(); i++) {
                try {
                    downloadThreads.get(i).join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    logErrorToDb("Interrupted during download join", e);
                }
            }

            System.out.println("All downloads completed. Starting sequential conversion...");

            for (int i = 0; i < filesToConvert.size(); i++) {
                FileToProcess file = filesToConvert.get(i);
                try {
                    convertDocToPdf(file.localDocPath, file.localDir);
                } catch (Exception e) {
                    System.err.println("Error converting: " + file.localDocPath);
                    e.printStackTrace();
                    logErrorToDb("Error converting DOC to PDF: " + file.localDocPath, e);
                }
            }

            System.out.println("All conversions completed. Starting uploads...");

            Vector<Thread> uploadThreads = new Vector<Thread>();
            for (int i = 0; i < filesToConvert.size(); i++) {
                final FileToProcess file = filesToConvert.get(i);

                Thread t = new Thread(new Runnable() {
                    public void run() {
                        uploadPdfToSmb(file, auth);
                    }
                });

                uploadThreads.add(t);
                t.start();
            }

            for (int i = 0; i < uploadThreads.size(); i++) {
                try {
                    uploadThreads.get(i).join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    logErrorToDb("Interrupted during upload join", e);
                }
            }

            System.out.println("All uploads completed. Processing done.");

        } catch (Exception e) {
            e.printStackTrace();
            logErrorToDb("Unhandled error during startProcessing", e);
        }
    }

    private void createAllTempDir() {
        for (int i = 0; i < dirV.size(); i++) {
            new FileManager().createDir(tempFolderPath + "/" + dirV.get(i));
        }
    }

    public void getAllDocPath() {
        pathV.removeAllElements();
        dirV.removeAllElements();

        ExamDBConnection db = new ExamDBConnection();
        ResultSet rs = db.retrieveAllExamPath();
        try {
            while (rs.next()) {
                pathV.add(rs.getString(1));
                dirV.add(rs.getString(2));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            logErrorToDb("SQL error while fetching doc paths", e);
        } finally {
            db.closeConnection();
        }
    }

    private void downloadDocsFromSmb(String smbPath, String localPath, NtlmPasswordAuthentication auth) {
        try {
            SmbFile smbDir = new SmbFile(smbPath, auth);
            if (!smbDir.exists() || !smbDir.isDirectory()) {
                System.err.println("Invalid SMB directory: " + smbPath);
                return;
            }

            new FileManager().createDir(localPath);
            SmbFile[] files = smbDir.listFiles();

            for (int i = 0; i < files.length; i++) {
                SmbFile file = files[i];
                if (file.isFile() && (file.getName().endsWith(".doc") || file.getName().endsWith(".docx"))) {
                    String localDocPath = currentDir + "/" + localPath + "/" + file.getName();
                    InputStream in = null;
                    OutputStream out = null;
                    try {
                        in = new BufferedInputStream(new SmbFileInputStream(file));
                        out = new BufferedOutputStream(new FileOutputStream(localDocPath));
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = in.read(buffer)) > 0) {
                            out.write(buffer, 0, len);
                        }
                        System.out.println("Downloaded: " + file.getName());
                    } finally {
                        if (in != null) in.close();
                        if (out != null) out.close();
                    }

                    synchronized (filesToConvert) {
                        int fileId = Integer.parseInt(dirV.get(i));
                        filesToConvert.add(new FileToProcess(file, localDocPath, currentDir + "/" + localPath, fileId));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logErrorToDb("Error downloading from SMB: " + smbPath, e);
        }
    }

    private void uploadPdfToSmb(FileToProcess file, NtlmPasswordAuthentication auth) {
        try {
            String pdfFileName = file.smbFile.getName().replaceAll("\\.docx?$", ".pdf");
            String localPdfPath = file.localDir + "/" + pdfFileName;

            File pdfFile = new File(localPdfPath);
            if (!pdfFile.exists()) {
                System.err.println("PDF not found: " + localPdfPath);
                return;
            }

            SmbFile pdfDest = new SmbFile(file.smbFile.getParent() + pdfFileName, auth);
            InputStream in = null;
            OutputStream out = null;
            try {
                in = new BufferedInputStream(new FileInputStream(pdfFile));
                out = new BufferedOutputStream(new SmbFileOutputStream(pdfDest));
                byte[] buffer = new byte[4096];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
                System.out.println("Uploaded: " + pdfFileName);

                ExamDBConnection db = new ExamDBConnection();
                db.updateExamDocPdfFlag(file.fileId);
                db.closeConnection();
            } finally {
                if (in != null) in.close();
                if (out != null) out.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
            logErrorToDb("Error uploading PDF: " + file.smbFile.getName(), e);
        }
    }

    public static void convertDocToPdf(String inputFilePath, String outputDir) throws IOException, InterruptedException {
        System.out.println("Converting: " + inputFilePath + " → PDF in: " + outputDir);

        String[] command = {
            "libreoffice",
            "--headless",
            "--nofirststartwizard",
            "--convert-to", "pdf",
            inputFilePath,
            "--outdir", outputDir
        };

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().putAll(System.getenv());
        Process process = pb.start();

        BufferedReader stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
        BufferedReader stdErr = new BufferedReader(new InputStreamReader(process.getErrorStream()));

        String line;
        while ((line = stdOut.readLine()) != null) {
            System.out.println("OUT: " + line);
        }
        while ((line = stdErr.readLine()) != null) {
            System.err.println("ERR: " + line);
        }

        int exitCode = process.waitFor();
        System.out.println("LibreOffice exited with code: " + exitCode);

        if (exitCode != 0) {
            throw new IOException("LibreOffice conversion failed with exit code: " + exitCode);
        }
    }

    private void logErrorToDb(String message, Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        String stackTrace = sw.toString();
        String fullError = message + "\n" + stackTrace;

        try {
            ExamDBConnection db = new ExamDBConnection();
            db.insertErrorLog(fullError);
            db.closeConnection();
        } catch (Exception dbEx) {
            System.err.println("Failed to log error to DB:");
            dbEx.printStackTrace();
        }
    }

    private static class FileToProcess {
        public SmbFile smbFile;
        public String localDocPath;
        public String localDir;
        public int fileId;

        public FileToProcess(SmbFile smbFile, String localDocPath, String localDir, int fileId) {
            this.smbFile = smbFile;
            this.localDocPath = localDocPath;
            this.localDir = localDir;
            this.fileId = fileId;
        }
    }
}
