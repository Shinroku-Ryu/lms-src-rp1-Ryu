package jp.co.sss.lms.service;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import jakarta.validation.Valid;
import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.entity.TStudentAttendance;
import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.mapper.TStudentAttendanceMapper;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;
import jp.co.sss.lms.util.DateUtil;
import jp.co.sss.lms.util.LoginUserUtil;
import jp.co.sss.lms.util.MessageUtil;
import jp.co.sss.lms.util.TrainingTime;

/**
 * 勤怠情報（受講生入力）サービス
 * 
 * @author 東京ITスクール
 */

@Service
public class StudentAttendanceService {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private AttendanceUtil attendanceUtil;
	@Autowired
	private MessageUtil messageUtil;
	@Autowired
	private LoginUserUtil loginUserUtil;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	private TStudentAttendanceMapper tStudentAttendanceMapper;

	/**
	 * 勤怠一覧情報取得
	 * 
	 * @param courseId
	 * @param lmsUserId
	 * @return 勤怠管理画面用DTOリスト
	 */
	public List<AttendanceManagementDto> getAttendanceManagement(Integer courseId,
			Integer lmsUserId) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = tStudentAttendanceMapper
				.getAttendanceManagement(courseId, lmsUserId, Constants.DB_FLG_FALSE);
		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			// 中抜け時間を設定
			if (dto.getBlankTime() != null) {
				TrainingTime blankTime = attendanceUtil.calcBlankTime(dto.getBlankTime());
				dto.setBlankTimeValue(String.valueOf(blankTime));
			}
			// 遅刻早退区分判定
			AttendanceStatusEnum statusEnum = AttendanceStatusEnum.getEnum(dto.getStatus());
			if (statusEnum != null) {
				dto.setStatusDispName(statusEnum.name);
			}
		}

		return attendanceManagementDtoList;
	}

	/**
	 * 出退勤更新前のチェック
	 * 
	 * @param attendanceType
	 * @return エラーメッセージ
	 */
	public String punchCheck(Short attendanceType) {
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 権限チェック
		if (!loginUserUtil.isStudent()) {
			return messageUtil.getMessage(Constants.VALID_KEY_AUTHORIZATION);
		}
		// 研修日チェック
		if (!attendanceUtil.isWorkDay(loginUserDto.getCourseId(), trainingDate)) {
			return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_NOTWORKDAY);
		}
		// 登録情報チェック
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		switch (attendanceType) {
		case Constants.CODE_VAL_ATWORK:
			if (tStudentAttendance != null
					&& !tStudentAttendance.getTrainingStartTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			break;
		case Constants.CODE_VAL_LEAVING:
			if (tStudentAttendance == null
					|| tStudentAttendance.getTrainingStartTime().equals("")) {
				// 出勤情報がないため退勤情報を入力出来ません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
			}
			if (!tStudentAttendance.getTrainingEndTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			TrainingTime trainingStartTime = new TrainingTime(
					tStudentAttendance.getTrainingStartTime());
			TrainingTime trainingEndTime = new TrainingTime();
			if (trainingStartTime.compareTo(trainingEndTime) > 0) {
				// 退勤時刻は出勤時刻より後でなければいけません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
			}
			break;
		}
		return null;
	}

	/**
	 * 出勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchIn() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 現在の研修時刻
		TrainingTime trainingStartTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				null);
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		if (tStudentAttendance == null) {
			// 登録処理
			tStudentAttendance = new TStudentAttendance();
			tStudentAttendance.setLmsUserId(loginUserDto.getLmsUserId());
			tStudentAttendance.setTrainingDate(trainingDate);
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setTrainingEndTime("");
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setNote("");
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setFirstCreateDate(date);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendance.setBlankTime(null);
			tStudentAttendanceMapper.insert(tStudentAttendance);
		} else {
			// 更新処理
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendanceMapper.update(tStudentAttendance);
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 退勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchOut() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		// 出退勤時刻
		TrainingTime trainingStartTime = new TrainingTime(
				tStudentAttendance.getTrainingStartTime());
		TrainingTime trainingEndTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				trainingEndTime);
		// 更新処理
		tStudentAttendance.setTrainingEndTime(trainingEndTime.toString());
		tStudentAttendance.setStatus(attendanceStatusEnum.code);
		tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
		tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
		tStudentAttendance.setLastModifiedDate(date);
		tStudentAttendanceMapper.update(tStudentAttendance);
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠フォームへ設定
	 * 
	 * @param attendanceManagementDtoList
	 * @return 勤怠編集フォーム
	 */
	public AttendanceForm setAttendanceForm(
			List<AttendanceManagementDto> attendanceManagementDtoList) {

		AttendanceForm attendanceForm = new AttendanceForm();
		attendanceForm.setAttendanceList(new ArrayList<DailyAttendanceForm>());
		attendanceForm.setLmsUserId(loginUserDto.getLmsUserId());
		attendanceForm.setUserName(loginUserDto.getUserName());
		attendanceForm.setLeaveFlg(loginUserDto.getLeaveFlg());
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());
		
		//Task.26 時間マップを取得
		attendanceForm.setTrainingHours(attendanceUtil.getHourMap());
		//Task.26 分マップを取得
		attendanceForm.setTrainingMinutes(attendanceUtil.getMinuteMap());

		// 途中退校している場合のみ設定
		if (loginUserDto.getLeaveDate() != null) {
			attendanceForm
					.setLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy-MM-dd"));
			attendanceForm.setDispLeaveDate(
					dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy年M月d日"));
		}

		// 勤怠管理リストの件数分、日次の勤怠フォームに移し替え
		for (AttendanceManagementDto attendanceManagementDto : attendanceManagementDtoList) {
			DailyAttendanceForm dailyAttendanceForm = new DailyAttendanceForm();
			dailyAttendanceForm
					.setStudentAttendanceId(attendanceManagementDto.getStudentAttendanceId());
			dailyAttendanceForm
					.setTrainingDate(dateUtil.toString(attendanceManagementDto.getTrainingDate()));
			dailyAttendanceForm
					.setTrainingStartTime(attendanceManagementDto.getTrainingStartTime());
			dailyAttendanceForm.setTrainingEndTime(attendanceManagementDto.getTrainingEndTime());
			if (attendanceManagementDto.getBlankTime() != null) {
				dailyAttendanceForm.setBlankTime(attendanceManagementDto.getBlankTime());
				dailyAttendanceForm.setBlankTimeValue(String.valueOf(
						attendanceUtil.calcBlankTime(attendanceManagementDto.getBlankTime())));
			}
			
			//劉 Task.26
			//Dtoから出勤時間を取得
			String trainingStartTime = attendanceManagementDto.getTrainingStartTime();
			//出勤時間の時間、分を日次の勤怠フォームに追加
			dailyAttendanceForm.setTrainingStartTimeHour(attendanceUtil.getHour(trainingStartTime));
			dailyAttendanceForm.setTrainingStartTimeMinute(attendanceUtil.getMinute(trainingStartTime));
			//出勤時間の時間、分を文字列に変換
			dailyAttendanceForm.setTrainingStartTimeHourValue(String.valueOf(attendanceUtil.getHour(trainingStartTime)));
			dailyAttendanceForm.setTrainingStartTimeMinuteValue(String.valueOf(attendanceUtil.getMinute(trainingStartTime)));
			//Dtoから退勤時間を取得
			String trainingEndTime = attendanceManagementDto.getTrainingEndTime();
			//退勤時間の時間、分を日次の勤怠フォームに追加
			dailyAttendanceForm.setTrainingEndTimeHour(attendanceUtil.getHour(trainingEndTime));
			dailyAttendanceForm.setTrainingEndTimeMinute(attendanceUtil.getMinute(trainingEndTime));
			//退勤時間の時間、分を文字列に変換
			dailyAttendanceForm.setTrainingEndTimeHourValue(String.valueOf(attendanceUtil.getHour(trainingEndTime)));
			dailyAttendanceForm.setTrainingEndTimeMinuteValue(String.valueOf(attendanceUtil.getMinute(trainingEndTime)));
			
			dailyAttendanceForm.setStatus(String.valueOf(attendanceManagementDto.getStatus()));
			dailyAttendanceForm.setNote(attendanceManagementDto.getNote());
			dailyAttendanceForm.setSectionName(attendanceManagementDto.getSectionName());
			dailyAttendanceForm.setIsToday(attendanceManagementDto.getIsToday());
			dailyAttendanceForm.setDispTrainingDate(dateUtil
					.dateToString(attendanceManagementDto.getTrainingDate(), "yyyy年M月d日(E)"));
			dailyAttendanceForm.setStatusDispName(attendanceManagementDto.getStatusDispName());

			attendanceForm.getAttendanceList().add(dailyAttendanceForm);
		}

		return attendanceForm;
	}
	
	
	public void updateCheck(@Valid AttendanceForm attendanceForm,BindingResult result) {
		
		for ( DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {
			
			int listNo = attendanceForm.getAttendanceList().indexOf(dailyAttendanceForm);
			
			//備考が字数制限を超える場合
			if(dailyAttendanceForm.getNote().length() > 100) {
				String noteErrorMsg = messageUtil.getMessage(Constants.VALID_KEY_MAXLENGTH,
					new String[]{"備考","100"});
				result.addError(new FieldError(result.getObjectName(), "attendanceList"+listNo+".note",noteErrorMsg));
			}
			//出勤時＆分の片方が未入力場合
			Integer startHour = dailyAttendanceForm.getTrainingStartTimeHour();
			Integer startMinute = dailyAttendanceForm.getTrainingStartTimeMinute();
			if((startHour == null && startMinute!= null) || (startHour!= null && startMinute == null)){
				String startTimeErrorMsg = messageUtil.getMessage(Constants.INPUT_INVALID,new String[]{"出勤時間"});
				result.addError(new ObjectError(result.getObjectName(), startTimeErrorMsg));
			}
			//退勤時＆分の片方が未入力の場合
			Integer endHour = dailyAttendanceForm.getTrainingEndTimeHour();
			Integer endMinute = dailyAttendanceForm.getTrainingEndTimeMinute();
			if ((endHour == null && endMinute!= null) || (endHour!= null && endMinute == null)) {
				String endTimeErrorMsg = messageUtil.getMessage(Constants.INPUT_INVALID,new String[]{"退勤時間"});
				result.addError(new ObjectError(result.getObjectName(), endTimeErrorMsg));
			}
			//出勤時間が未入力の場合
			String startTime = startHour + ":" + startMinute;;
			dailyAttendanceForm.setTrainingStartTime(startTime);
			String endTime = endHour + ":" + endMinute;;
			dailyAttendanceForm.setTrainingEndTime(endTime);
			
			if(dailyAttendanceForm.getTrainingStartTime() == " : " && dailyAttendanceForm.getTrainingEndTime() != " : " ) {
				String trainingTimeErrorMsg = messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
				result.addError(new ObjectError(result.getObjectName(), trainingTimeErrorMsg));
			}
		
		
		
		}
	}
//	dailyから取り出す
//	check(){
	
//	for (i=0 , ){
//	form. = set;
//	 result.addError(new FieldError(result.getObjectName(), ,endTimeErrorMsg));
	

	/**
	 * 勤怠登録・更新処理
	 * 
	 * @param attendanceForm
	 * @return 完了メッセージ
	 * @throws ParseException
	 */

	public String update(@Valid AttendanceForm attendanceForm) throws ParseException {

		Integer lmsUserId = loginUserUtil.isStudent() ? loginUserDto.getLmsUserId()
				: attendanceForm.getLmsUserId();

		// 現在の勤怠情報（受講生入力）リストを取得
		List<TStudentAttendance> tStudentAttendanceList = tStudentAttendanceMapper
				.findByLmsUserId(lmsUserId, Constants.DB_FLG_FALSE);

		// 入力された情報を更新用のエンティティに移し替え
		Date date = new Date();
		
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			// 更新用エンティティ作成
			TStudentAttendance tStudentAttendance = new TStudentAttendance();
			// 日次勤怠フォームから更新用のエンティティにコピー
			BeanUtils.copyProperties(dailyAttendanceForm, tStudentAttendance);
			// 研修日付
			tStudentAttendance
					.setTrainingDate(dateUtil.parse(dailyAttendanceForm.getTrainingDate()));
			// 現在の勤怠情報リストのうち、研修日が同じものを更新用エンティティで上書き
			for (TStudentAttendance entity : tStudentAttendanceList) {
				if (entity.getTrainingDate().equals(tStudentAttendance.getTrainingDate())) {
					tStudentAttendance = entity;
					break;
				}
			}
			tStudentAttendance.setLmsUserId(lmsUserId);
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			
			
			// Task.26 劉 出勤時刻整形
			TrainingTime trainingStartTime = null;
			Integer startHour = dailyAttendanceForm.getTrainingStartTimeHour();
			Integer startMinute = dailyAttendanceForm.getTrainingStartTimeMinute();
			String startTime;
			if(startHour == null || startMinute == null) {
				startTime = "";
				tStudentAttendance.setTrainingStartTime(startTime);
			}else {
				startTime = startHour + ":" + startMinute;
				trainingStartTime = new TrainingTime(startTime);
				tStudentAttendance.setTrainingStartTime(trainingStartTime.getFormattedString());
			}
			
////		
			
			// Task.26 劉 退勤時刻整形
			TrainingTime trainingEndTime = null;
			Integer endHour = dailyAttendanceForm.getTrainingEndTimeHour();
			Integer endMinute = dailyAttendanceForm.getTrainingEndTimeMinute();
			String endTime;
			
			if(endHour == null || endMinute == null) {
				endTime = "";
				tStudentAttendance.setTrainingEndTime(endTime);
			}else {
				endTime = endHour + ":" + endMinute;
				trainingEndTime = new TrainingTime(endTime);
				tStudentAttendance.setTrainingEndTime(trainingEndTime.getFormattedString());
			}
			
//			//Task.27-劉
//			
//	
			// 中抜け時間
			tStudentAttendance.setBlankTime(dailyAttendanceForm.getBlankTime());
			// 遅刻早退ステータス
			if ((trainingStartTime != null || trainingEndTime != null)
					&& !dailyAttendanceForm.getStatusDispName().equals("欠席")) {
				AttendanceStatusEnum attendanceStatusEnum = attendanceUtil
						.getStatus(trainingStartTime, trainingEndTime);
				tStudentAttendance.setStatus(attendanceStatusEnum.code);
			}
			// 備考
			tStudentAttendance.setNote(dailyAttendanceForm.getNote());
			
			// 更新者と更新日時
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			// 削除フラグ
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			// 登録用Listへ追加
			tStudentAttendanceList.add(tStudentAttendance);
		}
		
//		if(!errorMsg.isEmpty()) {
//		return errorMsg;
//		}
		
		// 登録・更新処理
		for (TStudentAttendance tStudentAttendance : tStudentAttendanceList) {
			if (tStudentAttendance.getStudentAttendanceId() == null) {
				tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
				tStudentAttendance.setFirstCreateDate(date);
				tStudentAttendanceMapper.insert(tStudentAttendance);
			} else {
				tStudentAttendanceMapper.update(tStudentAttendance);
			}
		}
		// 完了メッセージ
//			List<String> complete = new ArrayList<>();
//			
//			complete.add(messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE));
//			return complete;
//		もとの返却結果
//			if (result.hasErrors()) {
//				return result.toString();
//			}else
				return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
		
	}
	
	/**
	 * 勤怠未入力件数があるかどうかを判定
	 * 
	 * @author 劉-Task.25
	 * @param lmsUserId
	 * @return 判定結果
	 */
	public Boolean notEnterCount(Integer lmsUserId) {
		
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		date = attendanceUtil.getTrainingDate();
		
		Integer notEnterCount = 
				tStudentAttendanceMapper.notEnterCount(lmsUserId, Constants.DB_FLG_FALSE, date);
		//未入力件数が0以上の場合trueを返す
		if(notEnterCount > 0) {
			return true;
//			return notEnterCount>0だけでもOK
		}else {
			//0以下の場合はfalseを返す
			return false;
		}
	}

}
