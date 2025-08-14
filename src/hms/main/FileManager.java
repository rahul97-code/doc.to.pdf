package hms.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;

public class FileManager {

	public String[] ReadFile() {
		// TODO Auto-generated constructor stub
		String data[] = new String[10];
		// The name of the file to open.
		String fileName = "data.mdi";
		// This will reference one line at a time
		String line = null;

		try {
			// FileReader reads text files in the default encoding.
			FileReader fileReader = new FileReader(fileName);

			// Always wrap FileReader in BufferedReader.
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			String str = null;
			boolean fetch=true;
			while ((line = bufferedReader.readLine()) != null&&fetch) {
				// System.out.println(line);
				str = line;
				fetch=false;
			}
			int i = 0;
			for (String retval : str.split("@")) {
				data[i] = retval;
				i++;
			}
			
			// Always close files.
			bufferedReader.close();
		} catch (FileNotFoundException ex) {
			System.out.println("Unable to open file '" + fileName + "'");
		} catch (IOException ex) {
			System.out.println("Error reading file '" + fileName + "'");
			// Or we could just do this:
			// ex.printStackTrace();
		}
		return data;
	}
	
	public static void createDir(String path) {
	    File dir = new File(path);
	    if (!dir.exists()) {
	        dir.mkdirs();
	    }
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

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		FileManager d=new FileManager();
System.out.println(Arrays.toString(d.ReadFile()));
	}

}
