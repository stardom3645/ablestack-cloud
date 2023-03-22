const fs = require('fs')
const fs2 = require('fs')

const jsonBuffer = fs.readFileSync('./public/config.json')
const dataJson = jsonBuffer.toString()
const data = JSON.parse(dataJson)
const version = 'Diplo-v4.0.0'
// try {
// const version = fs2.readFileSync('/mnt/jenkins-work/versionInfo.txt', 'utf8')
// data.buildVersion = version

const m = new Date()
const date = m.getFullYear() + ('0' + (m.getMonth() + 1)).slice(-2) + ('0' + m.getDate()).slice(-2)
// data.buildVersion = version + '-' + date
data.buildVersion = version + '-' + date + '-dev'

// } catch (err) {
//   const m = new Date()
//   const date = m.getFullYear() + ('0' + (m.getMonth() + 1)).slice(-2) + ('0' + m.getDate()).slice(-2)
//   data.buildVersion = version + '-' + date
//   // data.buildVersion = version + '-' + date + '-dev'
// }

fs.writeFileSync('./public/config.json', JSON.stringify(data))
