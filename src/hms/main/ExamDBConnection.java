package hms.main;

import hms.main.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;

import javax.swing.JOptionPane;

public class ExamDBConnection extends DBConnection {
	Connection connection = null;
	Statement statement = null;
	ResultSet rs = null;

	public ExamDBConnection() {

		super();
		connection = getConnection();
		statement = getStatement();
	} 
	public ResultSet retrieveData(String query) {
		try {
			rs = statement.executeQuery(query);
		} catch (SQLException sqle) {
			JOptionPane.showMessageDialog(null, sqle.getMessage(), "ERROR",
					javax.swing.JOptionPane.ERROR_MESSAGE);
		}
		return rs;
	}
	
	public void insertErrorLog(String errorText) {
	    String sql = "INSERT INTO lis_debugging (problem, date_time,script_name) VALUES ("+errorText+", NOW(),'DOC_TO_PDF')";
	    try  {
	    	statement.executeUpdate(sql);
	    } catch (SQLException e) {
	        System.err.println("Error writing to error_logs table:");
	        e.printStackTrace();
	    }
	}

	
	public ResultSet retrieveAllExamPath() {
		String query = "SELECT CONCAT('/HMS/Patient/',exam_pid,'/Exam/',exam_id) as smbpath,exam_id as dir from exam_entery ee where doc_to_pdf ='0'";
		try {
			rs = statement.executeQuery(query);

		} catch (SQLException sqle) {
			JOptionPane.showMessageDialog(null, sqle.getMessage(), "ERROR",
					javax.swing.JOptionPane.ERROR_MESSAGE); 
		}
		return rs;
	}
	
	public void updateExamDocPdfFlag(int exam_id) throws Exception {
		statement.executeUpdate("update `exam_entery` set `doc_to_pdf` ='1' where `exam_id`='"+exam_id+"' ");
	}
	
	public void UpdateLisData(String[] data,String index){
		String insertSQL = "UPDATE `exam_entery` set `workorder_id`='"+data[1]+"',`lis_report_url`='"+data[2]+"' where `receipt_id`='"+index+"' and `lis_code`>'0'";

		try {
			statement.executeUpdate(insertSQL);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	public int inserDataIPDExam(String[] data) throws Exception {
		String insertSQL = "INSERT INTO `exam_entery`(`exam_name`, `exam_nameid`, `exam_pid`, `exam_pname`, `exam_doctorreff`, `exam_date`, `exam_charges`, `exam_note1`,`exam_room`,`exam_note2`,`exam_cat`,`exam_sample5`,`exam_chargespaid`,`receipt_id`,`p_insurancetype`,`exam_result5`,`lis_code`,`ipd_opd_no`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
		PreparedStatement preparedStatement = connection.prepareStatement(
				insertSQL, Statement.RETURN_GENERATED_KEYS);
		for (int i = 1; i < 19; i++) {

			preparedStatement.setString(i, data[i - 1]);
		}

		preparedStatement.executeUpdate();
		ResultSet rs = preparedStatement.getGeneratedKeys();
		rs.next();

		return rs.getInt(1);
	}
}
