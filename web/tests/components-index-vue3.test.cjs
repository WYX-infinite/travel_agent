const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const projectRoot = path.resolve(__dirname, '..')

function read(relativePath) {
  return fs.readFileSync(path.join(projectRoot, relativePath), 'utf8')
}

const componentFiles = [
  'src/components/Header.vue',
  'src/components/Footer.vue',
  'src/components/userIndex/DataShowcase.vue',
  'src/components/userIndex/HeroSection.vue',
  'src/components/userIndex/RecommendedSection.vue',
  'src/components/userIndex/StatsShowcase.vue',
  'src/views/Index.vue'
]

const source = componentFiles.map(read).join('\n')
const hero = read('src/components/userIndex/HeroSection.vue')
const recommended = read('src/components/userIndex/RecommendedSection.vue')
const index = read('src/views/Index.vue')

test('component tree contains no Vue 2 or Element UI template APIs', () => {
  assert.doesNotMatch(source, /\.sync|\sslot=|slot-scope|el-icon-/)
  assert.doesNotMatch(source, /(^|[^.$\w])axios\.(get|post)\(/m)
})

test('HeroSection uses Element Plus icons and declares emitted events', () => {
  assert.match(hero, /from ['"]@element-plus\/icons-vue['"]/) 
  assert.match(hero, /emits:\s*\[['"]departure-change['"]\]/)
  for (const icon of ['Location', 'Place', 'Search', 'DataAnalysis']) {
    assert.match(hero, new RegExp(`<${icon}\\s*/>`))
  }
})

test('RecommendedSection uses the installed Axios plugin', () => {
  assert.match(recommended, /this\.\$axios\s*\.get\(/)
})

test('Index uses only child interfaces that are actually implemented', () => {
  assert.doesNotMatch(index, /id="app"/)
  assert.doesNotMatch(index, /:user-name|:user-stats|@search|:recommended-items|@view/)
})

test('runtime dependencies are declared directly', () => {
  const packageJson = JSON.parse(read('package.json'))
  assert.ok(packageJson.dependencies['@element-plus/icons-vue'])
  assert.ok(packageJson.dependencies['element-china-area-data'])
})
