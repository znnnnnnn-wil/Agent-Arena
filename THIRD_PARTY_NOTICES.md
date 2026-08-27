# Third-party notices

Agent Arena 本身采用 [MIT License](LICENSE)。仓库中直接再分发的第三方代码和素材仍由各自的许可证约束。

## Three.js

- 项目：https://threejs.org/
- 源代码：https://github.com/mrdoob/three.js
- 版本：`0.180.0`
- 许可证：MIT
- 版权：Copyright 2010-2025 Three.js Authors
- 本地许可证：`src/main/resources/static/vendor/LICENSE.txt`

仓库中的 `three.core.min.js`、`three.module.min.js`、`GLTFLoader.js`、`BufferGeometryUtils.js` 和 `SkeletonUtils.js` 来自该项目。

## Quaternius 3D assets

- 作者：Quaternius
- 网站：https://quaternius.com/
- 许可证：CC0 1.0 Universal
- 详细来源、归档哈希和本地许可证：`src/main/resources/static/assets/3d/quaternius/THIRD_PARTY_ASSETS.md`

## Package dependencies

Maven 与 npm 依赖在构建时由各自的软件源获取，不随本仓库源代码直接再分发。依赖版本分别记录在 `pom.xml` 和 `package-lock.json`，使用者应同时遵守这些依赖各自的许可证。
