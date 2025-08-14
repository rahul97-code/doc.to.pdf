package hms.main;

import jcifs.smb.*;
import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    // Hold local downloaded doc files for sequential conversion
    private Vector<FileToProcess> filesToConvert = new Vector<FileToProcess>();

    public static void main(String[] args) {
        new DocProcessor().startProcessing();
    }

    public void startProcessing() {
    	new FileManager().deleteLocalTemp(new File(tempFolderPath));
    	
        getAllDocPath();
        new FileManager().createDir(tempFolderPath);
        createAllTempDir();

        final NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(DOMAIN, SMB_USER, SMB_PASS);

        // 1. Download files from SMB in parallel
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

        // Wait for all downloads to finish
        for (Thread t : downloadThreads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println("All downloads completed. Starting sequential conversion...");

        // 2. Convert files sequentially
        for (FileToProcess file : filesToConvert) {
            try {
                convertDocToPdf(file.localDocPath, file.localDir);
            } catch (Exception e) {
                System.err.println("Error converting: " + file.localDocPath);
                e.printStackTrace();
            }
        }

        System.out.println("All conversions completed. Starting uploads...");

        // 3. Upload converted PDFs to SMB in parallel
        Vector<Thread> uploadThreads = new Vector<Thread>();
        for (FileToProcess file : filesToConvert) {
            Thread t = new Thread(new Runnable() {
                public void run() {
                    uploadPdfToSmb(file, auth);
                }
            });
            uploadThreads.add(t);
            t.start();
        }

        // Wait for all uploads to finish
        for (Thread t : uploadThreads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println("All uploads completed. Processing done.");
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
        } finally {
            db.closeConnection();
        }
    }

    // Downloads DOC/DOCX files from SMB folder to local folder; adds them to filesToConvert vector
    private void downloadDocsFromSmb(String smbPath, String localPath, NtlmPasswordAuthentication auth) {
        try {
            SmbFile smbDir = new SmbFile(smbPath, auth);
            if (!smbDir.exists() || !smbDir.isDirectory()) {
                System.err.println("Invalid SMB directory: " + smbPath);
                return;
            }

            new FileManager().createDir(localPath); // Ensure local dir exists

            SmbFile[] files = smbDir.listFiles();
            for (int i = 0; i < files.length; i++) {
                SmbFile file = files[i];
                if (file.isFile() && (file.getName().endsWith(".doc") || file.getName().endsWith(".docx"))) {
                    String localDocPath = currentDir + "/" + localPath + "/" + file.getName();
                    // Download file
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
                    synchronized(filesToConvert) {
                    	int fileId = Integer.parseInt(dirV.get(i)); // assuming dirV holds IDs as strings
                    	filesToConvert.add(new FileToProcess(file, localDocPath, currentDir + "/" + localPath, fileId));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error downloading from SMB path: " + smbPath);
            e.printStackTrace();
        }
    }

    // Upload PDF back to SMB
    private void uploadPdfToSmb(FileToProcess file, NtlmPasswordAuthentication auth) {
        try {
            String pdfFileName = file.smbFile.getName().replaceAll("\\.docx?$", ".pdf");
            String localPdfPath = file.localDir + "/" + pdfFileName;
            System.out.println(file.localDir+" ikusdhgu");

            File pdfFile = new File(localPdfPath);
            if (!pdfFile.exists()) {
                System.err.println("PDF file not found, skipping upload: " + localPdfPath);
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
            System.err.println("Error uploading PDF: " + file.smbFile.getName());
            e.printStackTrace();
        }
    }

    // Convert DOC/DOCX to PDF via LibreOffice CLI (runs synchronously)
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

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.environment().putAll(System.getenv());

        Process process = processBuilder.start();

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
