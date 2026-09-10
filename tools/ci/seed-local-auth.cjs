// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
// Test harness only. Run from the pinned Seerr build after its database migrations.
const fs = require('node:fs');
const path = require('node:path');
const root = process.cwd();
const dataSource = require(path.join(root, 'dist/datasource.js')).default;
const { User } = require(path.join(root, 'dist/entity/User.js'));
const { UserType } = require(path.join(root, 'dist/constants/user.js'));
const { Permission } = require(path.join(root, 'dist/lib/permissions.js'));
const { getSettings } = require(path.join(root, 'dist/lib/settings/index.js'));

async function seed() {
  const input = JSON.parse(fs.readFileSync(0, 'utf8'));
  await dataSource.initialize();
  try {
    const users = dataSource.getRepository(User);
    if (await users.count()) throw new Error('Test bootstrap requires an empty user table');
    for (const [index, email] of ['admin@example.invalid', 'reader@example.invalid'].entries()) {
      const user = new User({
        email, username: index === 0 ? 'Admin' : 'Reader', avatar: '',
        userType: UserType.LOCAL, permissions: index === 0 ? Permission.ADMIN : Permission.REQUEST,
      });
      await user.setPassword(input.password);
      await users.save(user);
    }
    const settings = getSettings();
    settings.main.localLogin = true;
    await settings.save();
  } finally {
    await dataSource.destroy();
  }
}

seed().catch(() => { process.stderr.write('Local auth test bootstrap failed\n'); process.exitCode = 1; });
