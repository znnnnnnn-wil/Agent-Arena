const { startStaticServer } = require('./static-server.cjs');

module.exports = async () => {
  const server = await startStaticServer();
  return async () => {
    await new Promise((resolve, reject) => {
      server.close((error) => error ? reject(error) : resolve());
    });
  };
};
