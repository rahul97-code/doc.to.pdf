package hms.main;

import java.io.*;
import java.net.MalformedURLException;

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

public class LibreOfficeConverter2 {
    public static void main(String[] args) throws Exception {
        String inputFile = "/home/mdi-android-1/git/HMS/Digital Reports/AFB.doc";
        String outputDir = "/home/mdi-android-1/git/HMS/Digital Reports/";

        String[] command = {
            "libreoffice",
            "--headless",
            "--nofirststartwizard",
            "--convert-to",
            "pdf",
            inputFile,
            "--outdir",
            outputDir
        };

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File("/home/mdi-android-1/git/HMS"));
        pb.environment().putAll(System.getenv());
        Process process = pb.start();

        try (BufferedReader stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
             BufferedReader stdErr = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

            String line;
            while ((line = stdOut.readLine()) != null) {
                System.out.println("STDOUT: " + line);
            }
            while ((line = stdErr.readLine()) != null) {
                System.err.println("STDERR: " + line);
            }
        }

        int exitCode = process.waitFor();
        System.out.println("Exit code: " + exitCode);
    }
    
	public static boolean deleteLocalTemp(File directory) {

		if (directory.exists()) {
			File[] files = directory.listFiles();
			if (null != files) {
				for (int i = 0; i < files.length; i++) {
					if (files[i].isDirectory()) {
						deleteLocalTemp(files[i]);
					} else {
						files[i].delete();
					}
				}
			}
		}
		return (directory.delete());
	}
	
	private static void uploadOnSmb(String source, String dest)
			throws IOException {

		SmbFile remoteFile = new SmbFile(dest);
		OutputStream os = remoteFile.getOutputStream();
		InputStream is = new FileInputStream(new File(source));
		int bufferSize = 5096;
		byte[] b = new byte[bufferSize];
		int noOfBytes = 0;
		while ((noOfBytes = is.read(b)) != -1) {
			os.write(b, 0, noOfBytes);
		}
		os.close();
		is.close();
	}
	private void getFromSmb(String source, String dest)
			throws IOException {
		new File("localTemp").mkdir();

		SmbFile remoteFile = new SmbFile(source);
		OutputStream os = new FileOutputStream(dest);
		InputStream is = null;
		try {
			is = remoteFile.getInputStream();

		} catch (Exception e) {
			// TODO: handle exception
			return;
		}

		int bufferSize = 5096;

		byte[] b = new byte[bufferSize];
		int noOfBytes = 0;
		while ((noOfBytes = is.read(b)) != -1) {
			os.write(b, 0, noOfBytes);
		}
		os.close();
		is.close();

	}
	public String makeDirectory(String pid, String exam_id) {
		try {
			System.out.println("runinng");
			SmbFile dir = new SmbFile(mainDir + "/HMS/Patient/" + pid
					+ "/Exam/" + exam_id + "");
			if (!dir.exists()){
				dir.mkdirs();
			}
		} catch (SmbException | MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return mainDir + "/HMS/Patient/" + pid + "/Exam/" + exam_id;
	}
}
