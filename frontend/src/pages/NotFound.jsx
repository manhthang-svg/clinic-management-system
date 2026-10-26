
import { Link } from "react-router";

export default function NotFound() {
    return (
        <div className="min-h-screen flex flex-col items-center justify-center bg-gray-50 px-4 text-center">
            <h1 className="text-9xl font-bold text-teal-600">
                404
            </h1>

            <h2 className="mt-4 text-2xl font-semibold text-gray-800">
                Không tìm thấy trang
            </h2>

            <p className="mt-2 text-gray-500">
                Trang bạn đang tìm kiếm không tồn tại
                hoặc bạn không có quyền truy cập.
            </p>

            <Link
                to="/"
                className="mt-6 rounded-lg bg-teal-600 px-6 py-3 font-medium text-white transition hover:bg-teal-700"
            >
                Quay về trang chủ
            </Link>
        </div>
    );
}
