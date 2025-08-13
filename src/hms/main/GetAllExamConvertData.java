package hms.main;

import java.io.*;
import java.net.MalformedURLException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Vector;

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

public class GetAllExamConvertData {

	private String mainDir=new ReadFile().ReadFile()[1];
	Vector<String> pathV=new Vector<>();

	public static void main(String[] args) throws Exception {

		new GetAllExamConvertData();
	}

	public GetAllExamConvertData() {
		pathV.removeAllElements();
		// TODO Auto-generated constructor stub
		ExamDBConnection db=new ExamDBConnection();
		ResultSet rs=db.retrieveAllExamPath();
		try {
			while(rs.next()) {
				pathV.add(rs.getString(1));
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

fdg
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
