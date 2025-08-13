package hms.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class Developing_environment {
	public static boolean ACTIVE=true; 
	public Developing_environment() 
	{

		String fileName = "data.mdi";

		String content = null;
		File file = new File(fileName); // For example, foo.txt
		FileReader reader = null;
		try {
			reader = new FileReader(file);
			char[] chars = new char[(int) file.length()];
			reader.read(chars);
			content = new String(chars);
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if(reader != null){
				try {
					reader.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		//System.out.println(content);

		if(ACTIVE) {
			if(content.contains("192.168.1.33"))
			{
				content=content.replace("192.168.1.33", "192.51.11.206");
				WriteFile(fileName,content);
			}

		}else {
			if(content.contains("192.51.11.206"))
			{
				content=content.replace("192.51.11.206", "192.168.1.33");
				WriteFile(fileName,content);
			}
		}		

	}

	public void WriteFile(String fileName,String content) {
		try {
			System.out.println("writing...");
			// 3) write updated String in file
			FileWriter writer = new FileWriter(fileName);
			writer.write(content);
			writer.close();

		} catch (IOException e) {
			System.out.println(e);
			e.printStackTrace();
		} 
	}
}
