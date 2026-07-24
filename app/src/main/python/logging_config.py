import logging
import sys

def setup_logging(*args, **kwargs):
    logger = logging.getLogger()
    logger.setLevel(logging.DEBUG)
    
    # Очищаем старые хэндлеры, чтобы не было дублей
    if logger.hasHandlers():
        logger.handlers.clear()
        
    # Выводим логи только в консоль (Chaquopy сам направит их в Logcat Android Studio)
    console_handler = logging.StreamHandler(sys.stdout)
    formatter = logging.Formatter('%(asctime)s [%(levelname)s] %(name)s: %(message)s')
    console_handler.setFormatter(formatter)
    logger.addHandler(console_handler)

setup_logging()
