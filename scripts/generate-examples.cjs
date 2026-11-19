const XLSX=require('xlsx')
const fs=require('node:fs')
const path=require('node:path')
const dir=path.join(__dirname,'..','examples')
fs.mkdirSync(dir,{recursive:true})
const examples={
 '01_行政班.xlsx':[{行政班标识:'b_new',行政班名称:'软件2403',学院:'计算机与软件工程学院',专业:'软件'}],
 '02_账号.xlsx':[{用户标识:'s11',姓名:'导入学生',角色:'student',学号:'3243099011',行政班标识:'b_new'},{用户标识:'t3',姓名:'导入教师',角色:'teacher',学号:'',行政班标识:''}],
 '03_课程.xlsx':[{课程标识:'c4',课程名称:'移动应用实践'}],
 '04_教学班.xlsx':[{教学班标识:'tc3',教学班名称:'移动应用 C 班',课程标识:'c4',教师标识:'t3',学年:'2026-2027',学期:'第一学期'}],
 '05_选课关系.xlsx':[{学生标识:'s11',教学班标识:'tc3'}],
 '06_错误行示例.xlsx':[{用户标识:'s1',姓名:'重复标识测试',角色:'student',学号:'9900000091',行政班标识:'b1'},{用户标识:'s12',姓名:'错误行政班测试',角色:'student',学号:'9900000093',行政班标识:'不存在的班级'}]
}
for(const [name,data] of Object.entries(examples)){const book=XLSX.utils.book_new();XLSX.utils.book_append_sheet(book,XLSX.utils.json_to_sheet(data),'导入数据');XLSX.writeFile(book,path.join(dir,name))}
console.log('Generated 6 Excel examples in '+dir)
