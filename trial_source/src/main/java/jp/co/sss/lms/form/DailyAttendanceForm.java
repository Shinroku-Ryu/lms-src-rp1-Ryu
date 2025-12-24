package jp.co.sss.lms.form;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 日次の勤怠フォーム
 * 
 * @author 東京ITスクール
 */
@Data
public class DailyAttendanceForm {

	/** 受講生勤怠ID */
	private Integer studentAttendanceId;
	/** 途中退校日 */
	private String leaveDate;
	/** 日付 */
	private String trainingDate;
	/** 出勤時間 */
	private String trainingStartTime;
	
	/** Task.26 出勤時間（時）*/
	private Integer trainingStartTimeHour;
	/** Task.26 画面表示用出勤時間（時）*/
	private String trainingStartTimeHourValue;
	/** Task.26 出勤時間（分） */
	private Integer trainingStartTimeMinute;
	/** Task.26 画面表示用出勤時間（分）*/
	private String trainingStartTimeMinuteValue;
	
	/** 退勤時間 */
	private String trainingEndTime;
	
	/** Task.26 退勤時間（時間） */
	private Integer trainingEndTimeHour;
	/** Task.26 画面表示用退勤時間（時間） */
	private String trainingEndTimeHourValue;
	/** Task.26 退勤時間（分） */
	private Integer trainingEndTimeMinute;
	/** Task.26 画面表示用退勤時間（分） */
	private String trainingEndTimeMinuteValue;
	
	/** 中抜け時間 */
	private Integer blankTime;
	/** 中抜け時間（画面表示用） */
	private String blankTimeValue;
	/** ステータス */
	private String status;
	/** 備考 */
	@Size(max=100)
	private String note;
	/** セクション名 */
	private String sectionName;
	/** 当日フラグ */
	private Boolean isToday;
	/** エラーフラグ */
	private Boolean isError;
	/** 日付（画面表示用） */
	private String dispTrainingDate;
	/** ステータス（画面表示用） */
	private String statusDispName;
	/** LMSユーザーID */
	private String lmsUserId;
	/** ユーザー名 */
	private String userName;
	/** コース名 */
	private String courseName;
	/** インデックス */
	private String index;

}
