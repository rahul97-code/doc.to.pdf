package hms.main;

import jcifs.smb.*;
import java.io.*;
import javax.swing.*;
import hms.doc.scanning.database.DocScanDBConnection;
import hms.main.Developing_environment;

public class SmbFileUploader implements Runnable {

	private static String SMB_SERVER = "192.168.1.138";  // Samba server IP
	private static final String SMB_SHARE = "/data/MS/ScannedBillSlips/";  // Share name

	private static String smbUsername = "hospital";
	private static String smbPassword = "rotaryhospital";

	private final File localFolder;

	public static void main(String[] args) {
		uploadAllPdfsInThread("ScannedSlips/ScannedPdf/");
	}

	public SmbFileUploader(File localFolder) {
		if (new Developing_environment().ACTIVE) {
			SMB_SERVER = "192.51.11.206";
			smbUsername = null;
			smbPassword = null;
		}

		this.localFolder = localFolder;
	}

	@Override
	public void run() {

		if (!localFolder.exists() || !localFolder.isDirectory()) {
			showError("Local folder not found: " + localFolder.getAbsolutePath());
			return;
		}

		File[] pdfFiles = localFolder.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".pdf");
			}
		});

		if (pdfFiles == null) {
			System.out.println("listFiles() returned null — maybe no permission to read the folder.");
			return;
		}

		if (pdfFiles.length == 0) {
			System.out.println("No PDF files found in: " + localFolder.getAbsolutePath());
			return;
		}

		System.out.println("Found " + pdfFiles.length + " PDF file(s) in: " + localFolder.getAbsolutePath());

		for (int i = 0; i < pdfFiles.length; i++) {
			File pdfFile = pdfFiles[i];
			String fileNameWithoutExt = pdfFile.getName().replaceFirst("[.][^.]+$", "");
			String remoteRelativePath = fileNameWithoutExt + "/" + pdfFile.getName();

			boolean uploaded = uploadSingleFile(pdfFile, remoteRelativePath, fileNameWithoutExt);

			if (uploaded) {
				if (pdfFile.delete()) {
					System.out.println("Deleted local file: " + pdfFile.getAbsolutePath());
				} else {
					System.err.println("Failed to delete local file: " + pdfFile.getAbsolutePath());
				}
			}
		}
	}

	private boolean uploadSingleFile(File file, String remoteRelativePath, String billId) {
		InputStream in = null;
		OutputStream out = null;

		try {
			in = new FileInputStream(file);

			NtlmPasswordAuthentication auth =new NtlmPasswordAuthentication(null, smbUsername, smbPassword);

			String smbFolderUrl = "smb://" + SMB_SERVER + SMB_SHARE;
			String smbFileUrl = smbFolderUrl + remoteRelativePath;

			System.out.println("Uploading to: " + smbFileUrl);

			SmbFile smbFile = new SmbFile(smbFileUrl, auth);
			SmbFile parentDir = new SmbFile(smbFile.getParent(), auth);

			if (!parentDir.exists()) {
				System.out.println("Creating remote directory: " + parentDir.getPath());
				parentDir.mkdirs();
			}

			out = new SmbFileOutputStream(smbFile);

			byte[] buffer = new byte[32768];
			int bytesRead;

			System.out.println("Start uploading file: " + file.getName());
			while ((bytesRead = in.read(buffer)) != -1) {
				out.write(buffer, 0, bytesRead);
			}

			System.out.println("File uploaded successfully: " + file.getName());

			DocScanDBConnection db = new DocScanDBConnection();
			db.updateScannedBillFlag(billId);
			db.closeConnection();

			return true;

		} catch (Exception e) {
			final String errorMessage = "Error uploading file: " + file.getName() + "\n" + e.getMessage();
			System.err.println(errorMessage);
			e.printStackTrace();
			showError(errorMessage);
			return false;
		} finally {
			try { if (in != null) in.close(); } catch (IOException ignored) {}
			try { if (out != null) out.close(); } catch (IOException ignored) {}
		}
	}

	public static void uploadAllPdfsInThread(String folderName) {
		File folder = new File(folderName);
		SmbFileUploader uploader = new SmbFileUploader(folder);
		new Thread(uploader).start();
	}

	private void showError(final String message) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				JOptionPane.showMessageDialog(null, message, "Upload Error", JOptionPane.ERROR_MESSAGE);
			}
		});
	}
}
